package com.sgkrashi.review.mapper;

import com.sgkrashi.review.dto.response.ReviewResponse;
import com.sgkrashi.review.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    /** Name the erasure job writes into {@code users.name}; shown as is, not shortened to "Deleted u.". */
    private static final String ANONYMIZED_NAME = "Deleted user";

    public ReviewResponse toResponse(Review review, String reviewerName) {
        return new ReviewResponse(
                review.getId(),
                review.getTargetType(),
                review.getTargetId(),
                publicReviewerName(reviewerName),
                review.getRating(),
                review.getComment(),
                review.getCreatedAt()
        );
    }

    /**
     * Public reviews show "FirstName L." (first word plus the first letter of the last word),
     * not the full registered name. A single-word name is shown as is. Only the response is
     * shortened; the stored name is untouched. Null or blank stays null, as before.
     */
    static String publicReviewerName(String fullName) {
        if (fullName == null) {
            return null;
        }
        String trimmed = fullName.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (ANONYMIZED_NAME.equals(trimmed)) {
            return trimmed;
        }
        String[] words = trimmed.split("\s+");
        if (words.length == 1) {
            return words[0];
        }
        String last = words[words.length - 1];
        int initial = last.codePointAt(0);
        return words[0] + " " + new String(Character.toChars(Character.toUpperCase(initial))) + ".";
    }
}
