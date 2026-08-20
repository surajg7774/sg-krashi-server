package com.sgkrashi.chatassistant.service.impl;

import com.sgkrashi.auth.security.CurrentUserProvider;
import com.sgkrashi.booking.dto.response.BookingResponse;
import com.sgkrashi.booking.service.BookingService;
import com.sgkrashi.chatassistant.dto.response.ChatMessageResponse;
import com.sgkrashi.chatassistant.dto.response.ChatSessionResponse;
import com.sgkrashi.chatassistant.entity.ChatMessage;
import com.sgkrashi.chatassistant.entity.ChatMessageRole;
import com.sgkrashi.chatassistant.entity.ChatSession;
import com.sgkrashi.chatassistant.exception.ChatAssistantDisabledException;
import com.sgkrashi.chatassistant.knowledge.entity.PlatformKnowledgeEntry;
import com.sgkrashi.chatassistant.knowledge.service.PlatformKnowledgeService;
import com.sgkrashi.chatassistant.provider.ChatAssistantProvider;
import com.sgkrashi.chatassistant.provider.ChatTurn;
import com.sgkrashi.chatassistant.ratelimit.ChatRateLimiter;
import com.sgkrashi.chatassistant.repository.ChatMessageRepository;
import com.sgkrashi.chatassistant.repository.ChatSessionRepository;
import com.sgkrashi.chatassistant.service.ChatService;
import com.sgkrashi.common.exception.RateLimitExceededException;
import com.sgkrashi.common.exception.ResourceNotFoundException;
import com.sgkrashi.inquiry.dto.response.InquiryResponse;
import com.sgkrashi.inquiry.service.InquiryService;
import com.sgkrashi.order.dto.response.OrderSummaryResponse;
import com.sgkrashi.order.service.OrderService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * <h2>The security-critical class in this feature</h2>
 *
 * <p>{@link #buildPersonalDataContext()} is the one method that decides what
 * personal account data the model ever sees. It calls exactly the same
 * ownership-scoped methods the "My Orders"/"My Bookings"/"My Inquiries"
 * pages already use ({@code OrderService#listMyOrders}, {@code
 * BookingService#listMyBookings}, {@code InquiryService#getMyInquiries}) —
 * none of which accept a user id parameter at all. Each resolves the
 * caller's own identity internally, from {@link CurrentUserProvider} reading
 * the {@code SecurityContext} of this exact request thread. There is no
 * parameter here a crafted chat message could redirect to fetch someone
 * else's data with, because there is no such parameter to redirect — this is
 * a structural guarantee, not a prompt instruction {@link
 * com.sgkrashi.chatassistant.provider.GeminiChatProvider} could be talked
 * out of. The keyword check in {@link #looksLikePersonalDataQuestion} only
 * ever decides <i>whether</i> to fetch (and, for a Guest, decides to say "log
 * in" instead) — never <i>whose</i> data or <i>which</i> specific record.
 */
@Service
public class ChatServiceImpl implements ChatService {

    private static final int MAX_GROUNDING_ENTRIES = 4;
    private static final int MAX_PERSONAL_RECORDS = 5;
    private static final String DISABLED_MESSAGE = "The chat assistant is temporarily unavailable. Please check back later.";

    // Deliberately broad/simple (task spec: "keyword-based is fine for V1,
    // doesn't need to be sophisticated") — a false positive here just means
    // an unnecessary-but-harmless fetch of the caller's OWN data; a false
    // negative just means a personal question gets a generic answer instead.
    // Neither has any security consequence, since what gets fetched is
    // always scoped to the caller regardless of why the fetch happened.
    private static final List<String> PERSONAL_DATA_KEYWORDS = List.of(
            "my order", "my orders", "my booking", "my bookings", "my inquiry", "my inquiries",
            "my account", "my purchase", "my payment", "my refund", "status of my", "track my",
            "where is my", "did i order", "have i booked", "my last order", "my recent order",
            "my recent booking"
    );

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final PlatformKnowledgeService platformKnowledgeService;
    private final ChatAssistantProvider chatAssistantProvider;
    private final ChatRateLimiter rateLimiter;
    private final CurrentUserProvider currentUserProvider;
    private final OrderService orderService;
    private final BookingService bookingService;
    private final InquiryService inquiryService;
    private final boolean chatAssistantEnabled;

    public ChatServiceImpl(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            PlatformKnowledgeService platformKnowledgeService,
            ChatAssistantProvider chatAssistantProvider,
            ChatRateLimiter rateLimiter,
            CurrentUserProvider currentUserProvider,
            OrderService orderService,
            BookingService bookingService,
            InquiryService inquiryService,
            @Value("${app.chat-assistant.enabled:true}") boolean chatAssistantEnabled
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.platformKnowledgeService = platformKnowledgeService;
        this.chatAssistantProvider = chatAssistantProvider;
        this.rateLimiter = rateLimiter;
        this.currentUserProvider = currentUserProvider;
        this.orderService = orderService;
        this.bookingService = bookingService;
        this.inquiryService = inquiryService;
        this.chatAssistantEnabled = chatAssistantEnabled;
    }

    @Override
    @Transactional
    public ChatSessionResponse createSession() {
        if (!chatAssistantEnabled) {
            throw new ChatAssistantDisabledException(DISABLED_MESSAGE);
        }
        ChatSession session = new ChatSession();
        session.setUserId(currentUserProvider.getCurrentUserIdOrNull());
        ChatSession saved = chatSessionRepository.save(session);
        return new ChatSessionResponse(saved.getId(), List.of());
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long sessionId, String message, String clientIp) {
        if (!chatAssistantEnabled) {
            throw new ChatAssistantDisabledException(DISABLED_MESSAGE);
        }
        ChatSession session = getSessionEntityOrThrow(sessionId);
        Long currentUserId = currentUserProvider.getCurrentUserIdOrNull();

        // A session with a real owner can only be posted into by that same
        // authenticated user — same 404-not-403 convention as everywhere
        // else (AddressServiceImpl, CropDoctorServiceImpl). A Guest session
        // (userId null) has no owner to check, same as a guest-submitted
        // Inquiry — that openness is irrelevant to the security property
        // this class exists to guarantee: the personal-data fetch below is
        // always scoped to whoever the CURRENT caller is, never to
        // whichever browser originally created the session.
        if (session.getUserId() != null && !session.getUserId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Chat session not found");
        }

        String rateLimitKey = currentUserId != null ? "user:" + currentUserId : "ip:" + clientIp;
        if (!rateLimiter.tryConsume(rateLimitKey)) {
            throw new RateLimitExceededException("Too many messages. Please try again in a while.");
        }

        ChatMessage userMessage = new ChatMessage();
        userMessage.setSessionId(sessionId);
        userMessage.setRole(ChatMessageRole.USER);
        userMessage.setContent(message);
        ChatMessage savedUserMessage = chatMessageRepository.save(userMessage);

        List<ChatTurn> history = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .filter(m -> !m.getId().equals(savedUserMessage.getId()))
                .map(m -> new ChatTurn(m.getRole() == ChatMessageRole.ASSISTANT ? "assistant" : "user", m.getContent()))
                .toList();

        String groundingContext = buildGroundingText(platformKnowledgeService.retrieve(message, MAX_GROUNDING_ENTRIES));

        String personalDataContext = null;
        boolean guestAskedPersonalData = false;
        if (looksLikePersonalDataQuestion(message)) {
            if (currentUserId != null) {
                personalDataContext = buildPersonalDataContext();
            } else {
                guestAskedPersonalData = true;
            }
        }

        String replyText = chatAssistantProvider.reply(history, message, groundingContext, personalDataContext, guestAskedPersonalData);

        ChatMessage assistantMessage = new ChatMessage();
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setRole(ChatMessageRole.ASSISTANT);
        assistantMessage.setContent(replyText);
        ChatMessage savedAssistantMessage = chatMessageRepository.save(assistantMessage);

        return toResponse(savedAssistantMessage);
    }

    @Override
    public ChatSessionResponse getSession(Long sessionId) {
        ChatSession session = getSessionEntityOrThrow(sessionId);
        Long currentUserId = currentUserProvider.getCurrentUserId();
        if (session.getUserId() == null || !session.getUserId().equals(currentUserId)) {
            throw new ResourceNotFoundException("Chat session not found");
        }
        List<ChatMessageResponse> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(this::toResponse)
                .toList();
        return new ChatSessionResponse(session.getId(), messages);
    }

    private ChatSession getSessionEntityOrThrow(Long sessionId) {
        return chatSessionRepository.findById(sessionId)
                .filter(ChatSession::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Chat session not found"));
    }

    private boolean looksLikePersonalDataQuestion(String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        return PERSONAL_DATA_KEYWORDS.stream().anyMatch(lower::contains);
    }

    /** See this class's own Javadoc — this is the method the security guarantee rests on. */
    private String buildPersonalDataContext() {
        StringBuilder text = new StringBuilder();

        List<OrderSummaryResponse> orders = orderService.listMyOrders(0, MAX_PERSONAL_RECORDS).items();
        if (!orders.isEmpty()) {
            text.append("This user's recent orders:\n");
            for (OrderSummaryResponse order : orders) {
                text.append("- Order ").append(order.orderNumber()).append(": status ").append(order.status())
                        .append(", ").append(order.itemCount()).append(" item(s), total Rs. ").append(order.totalAmount())
                        .append(", placed ").append(order.createdAt()).append("\n");
            }
        }

        List<BookingResponse> bookings = bookingService.listMyBookings(0, MAX_PERSONAL_RECORDS).items();
        if (!bookings.isEmpty()) {
            text.append("This user's recent bookings:\n");
            for (BookingResponse booking : bookings) {
                text.append("- ").append(booking.bookableName()).append(" (").append(booking.bookableType())
                        .append("): status ").append(booking.status()).append(", ").append(booking.startDate())
                        .append(" to ").append(booking.endDate()).append(", total Rs. ").append(booking.totalPrice()).append("\n");
            }
        }

        List<InquiryResponse> inquiries = inquiryService.getMyInquiries(0, MAX_PERSONAL_RECORDS).items();
        if (!inquiries.isEmpty()) {
            text.append("This user's recent inquiries:\n");
            for (InquiryResponse inquiry : inquiries) {
                text.append("- ").append(inquiry.moduleType()).append(" inquiry: status ").append(inquiry.status())
                        .append(", submitted ").append(inquiry.createdAt()).append("\n");
            }
        }

        return text.isEmpty() ? "This user has no orders, bookings, or inquiries yet." : text.toString();
    }

    private String buildGroundingText(List<PlatformKnowledgeEntry> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder();
        for (PlatformKnowledgeEntry entry : entries) {
            text.append("### ").append(entry.getTitle()).append(" (").append(entry.getCategory()).append(")\n")
                    .append(entry.getContent()).append("\n\n");
        }
        return text.toString();
    }

    private ChatMessageResponse toResponse(ChatMessage message) {
        String role = message.getRole() == ChatMessageRole.ASSISTANT ? "assistant" : "user";
        return new ChatMessageResponse(message.getId(), role, message.getContent(), message.getCreatedAt());
    }
}
