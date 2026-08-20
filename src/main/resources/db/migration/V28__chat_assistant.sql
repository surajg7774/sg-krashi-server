-- V28__chat_assistant.sql
-- Chat sessions/messages for the AI Chat Assistant, plus a NEW, separate
-- platform-FAQ knowledge base (deliberately not mixed into
-- knowledge_base_entries — that table is crop-disease content keyed by
-- `crop`; this one is platform Q&A keyed by `category`, a different domain
-- entirely, hence its own table rather than a shared one with a
-- discriminator column bolted on).

CREATE TABLE chat_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- Nullable: Guests get sessions too (see ChatSession's Javadoc) — same
    -- "guest data with no owner" convention crop_listings.farmer_id and
    -- inquiries.user_id already use.
    user_id BIGINT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_chat_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_chat_sessions_user_id ON chat_sessions (user_id);

CREATE TABLE chat_messages (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    -- 'user' or 'assistant' — plain VARCHAR, not an enum type, matching this
    -- project's existing convention of storing enum-like values as strings
    -- (see OrderStatus/BookingStatus columns).
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_chat_messages_session FOREIGN KEY (session_id) REFERENCES chat_sessions (id)
);

CREATE INDEX idx_chat_messages_session_id ON chat_messages (session_id);

CREATE TABLE platform_knowledge_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category VARCHAR(150) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    -- Same JSON-serialized-float-array convention as
    -- knowledge_base_entries.embedding — see that column's comment
    -- (V27__knowledge_base_embeddings.sql) for why JSON, not a native vector
    -- type. Nullable for the same reason: populated by
    -- PlatformKnowledgeEmbeddingBackfillRunner after this migration seeds
    -- the rows, not inline here (a real Gemini API call per entry doesn't
    -- belong in a schema migration).
    embedding JSON NULL,
    source VARCHAR(300) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_platform_knowledge_entries_category ON platform_knowledge_entries (category);

INSERT INTO platform_knowledge_entries (category, title, content, source, created_at, updated_at, is_active) VALUES

('Product Store', 'How the Product Store works', 'The Product Store sells packaged farm goods (pulses, grains, and other pantry staples) with fixed prices and stock quantities, browsable by category. Add items to your cart, check out with a saved delivery address, and pay via Razorpay. Orders start as Pending Payment, move to Confirmed once payment is verified, and stay that way until refunded (if applicable). There is no separate "shipped/delivered" tracking stage in the current system - Confirmed is the final non-refunded status.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Product Store', 'Product Store checkout and payment', 'Checkout requires a saved delivery address (add one under My Addresses if you have not already) and a Razorpay payment. Stock is reserved the moment you complete checkout, before payment is confirmed, so quantities shown in the store are always accurate for what is actually available.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Crop Marketplace', 'How the Crop Marketplace works', 'The Crop Marketplace lists crop batches (grains, pulses, vegetables) sold directly, in bulk, with a harvest date and a quantity available - similar to the Product Store but for farm-fresh crop batches rather than packaged goods. Some listings are marked Organic Certified. Checkout, payment, and order status work the same way as the Product Store.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Crop Marketplace', 'Crop Marketplace filters', 'You can filter Crop Marketplace listings by crop type (grains, pulses, vegetables), price range, harvest date range, and organic-certified status, and search by name.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Equipment Rental', 'How Equipment Rental works', 'Equipment Rental lets you book farm equipment (tractors, tillage equipment, irrigation kits, and similar) for a specific date range at a daily rate. Check availability on the equipment''s own page (a calendar shows already-booked dates), then book your dates. Payment via Razorpay confirms the booking.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Equipment Rental', 'Equipment Rental cancellation policy', 'Equipment Rental bookings can be cancelled free of charge up to 48 hours before the booking''s start date. Cancelling within that 48-hour window is not permitted through self-service - contact support if you have an exceptional circumstance.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Farm Stay', 'How Farm Stay works', 'Farm Stay lets you book a stay at a farm property for a date range, similar to Equipment Rental but priced per night rather than per day, and some listings have a maximum guest count. Check the listing''s availability calendar, then book.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Farm Stay', 'Farm Stay cancellation policy', 'Farm Stay bookings can be cancelled free of charge up to 7 days before the stay''s start date - a longer window than Equipment Rental, since farm stay planning (and any prep the host does) needs more lead time. Cancelling within that 7-day window is not permitted through self-service.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Organic Farming', 'Organic Farming business line', 'The Organic Farming business line covers certified-organic produce and farming practices. Visit its page to learn more, or submit a visit/wholesale inquiry through the contact form there - a team member follows up directly rather than this being a fully self-service checkout flow.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Dairy Farm', 'Dairy Farm business line', 'The Dairy Farm business line covers dairy products and farm visits. Visit its page to learn more, or submit an inquiry through the contact form there for questions, wholesale interest, or a farm visit request.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Orders and Bookings', 'Order and booking statuses explained', 'An Order (Product Store or Crop Marketplace) moves through: Pending Payment (awaiting checkout payment), Confirmed (payment received), Payment Failed (payment did not go through - any stock reserved is released), or Refunded. A Booking (Equipment Rental or Farm Stay) moves through: Pending Payment, Confirmed, Cancelled, or Completed.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Orders and Bookings', 'How to check order or booking status', 'Logged-in customers can see all their orders under My Orders and all their bookings under My Bookings, both reachable from the account menu. Each shows its current status and a full history.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Refunds', 'How refunds work', 'Refunds are processed through Razorpay, the same payment gateway used for checkout, back to the original payment method. Once a refund is processed, the related order or booking is marked accordingly (Refunded for an order; Cancelled for a booking, since a refunded booking is a cancelled one). Refunds are not instant on the bank/card side even after being processed on our end - Razorpay''s own settlement timelines apply, typically a few business days.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Refunds', 'Refund eligibility', 'Refund eligibility depends on the specific order/booking and its cancellation policy (see the Equipment Rental and Farm Stay cancellation policy entries for booking-specific windows). If you believe you are owed a refund and it has not been processed, contact support with your order or booking reference.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Payments', 'Accepted payment methods', 'All payments on the platform go through Razorpay, which supports UPI, credit/debit cards, net banking, and popular wallets, depending on what your bank/card issuer supports through Razorpay.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('AI Crop Doctor', 'How AI Crop Doctor works', 'AI Crop Doctor lets you upload 1-3 photos of a plant, declare which crop it is, and get an AI-generated analysis: identified crop, health status, likely problem and pathogen (if any), confidence level, symptoms, causes, recommended actions, prevention tips, and monitoring guidance. It is available to Guests (results are not saved) and to logged-in Customers (results are saved to My Scans and can be downloaded as a PDF report).', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('AI Crop Doctor', 'AI Crop Doctor accuracy and honesty', 'AI Crop Doctor is honest about uncertainty - if the photo doesn''t give it enough to make a confident diagnosis, it says so explicitly (marked as Uncertain with a Low confidence level) rather than guessing. It also flags when the crop you declared does not visually match what''s in the photo, rather than silently assuming you were right.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('AI Crop Doctor', 'AI Crop Doctor scan limits', 'AI Crop Doctor scans are rate-limited (a maximum number of scans per hour) since each one costs real AI processing - if you hit the limit you''ll see a clear message and can try again shortly after.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Account', 'Creating an account', 'You can register with your name, email, and a password. A verified account lets you check out, save addresses, track orders/bookings/inquiries, and use AI Crop Doctor with saved scan history - Guests can browse and use AI Crop Doctor (without saved history), but cannot check out or book without an account.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Account', 'Forgot password', 'If you forget your password, use the "Forgot password" link on the login page - a reset link is emailed to the address on your account.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Account', 'Managing delivery addresses', 'Logged-in customers can add, edit, and remove delivery addresses under My Addresses, and mark one as the default used for checkout.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Inquiries', 'How the Contact/Inquiry form works', 'The Contact page (and each business line''s own contact form) lets you submit a question or request - no account required. If you''re logged in when you submit one, it''s automatically linked to your account and visible under My Inquiries; if you''re a Guest, it isn''t linked to any account. A team member responds; you''ll see its status (New, Contacted, Converted, or Closed) update under My Inquiries if you were logged in when you submitted it.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Support', 'Contact information', 'For questions not answered here, email sgkrashi@gmail.com or use the Contact page. The farm is located at SG Krashi Farm, Sirpur village, Khandwa district, Madhya Pradesh, India.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Reviews', 'Leaving a review', 'Logged-in customers who have completed an order or booking for a specific item can leave a rating and written review for it - reviews are tied to a real completed purchase, not open to anyone.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE),

('Notifications', 'How notifications work', 'You''ll get an email and an in-app notification (the bell icon) for order confirmations, booking confirmations, payment failures, refunds, cancellations, and inquiry status updates - both for automatic events (like a successful payment) and for actions an admin takes on your order/booking/inquiry.', 'SG Krashi platform documentation', NOW(6), NOW(6), TRUE);
