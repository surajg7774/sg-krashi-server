package com.sgkrashi.review.mapper;

import com.sgkrashi.review.dto.response.ReviewResponse;
import com.sgkrashi.review.entity.Review;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReviewerNameTest {

    @Test
    void firstNamePlusInitialOfTheLastName() {
        assertEquals("Suraj G.", ReviewMapper.publicReviewerName("Suraj Gupta"));
        assertEquals("Ramesh K.", ReviewMapper.publicReviewerName("Ramesh Kumar"));
    }

    @Test
    void middleNamesAreDroppedAndTheLastWordGivesTheInitial() {
        assertEquals("Ramesh S.", ReviewMapper.publicReviewerName("Ramesh Kumar Singh"));
    }

    @Test
    void paddingIsTrimmedAndTheInitialIsUppercased() {
        assertEquals("asha P.", ReviewMapper.publicReviewerName("  asha   patil  "));
    }

    @Test
    void aSingleWordNameStaysAsIs() {
        assertEquals("Madonna", ReviewMapper.publicReviewerName("Madonna"));
        assertEquals("Madonna", ReviewMapper.publicReviewerName("  Madonna "));
    }

    @Test
    void nonLatinAndEmojiInitialsAreKeptWhole() {
        assertEquals("सुरज ग.", ReviewMapper.publicReviewerName("सुरज गुप्ता"));
        assertEquals("Sam \uD83D\uDE00.", ReviewMapper.publicReviewerName("Sam \uD83D\uDE00smith"));
    }

    @Test
    void nullAndBlankStayNullAndTheErasedPlaceholderIsNotShortened() {
        assertNull(ReviewMapper.publicReviewerName(null));
        assertNull(ReviewMapper.publicReviewerName("   "));
        assertEquals("Deleted user", ReviewMapper.publicReviewerName("Deleted user"));
    }

    @Test
    void theMapperAppliesItToTheResponseOnly() {
        Review review = new Review();
        ReviewResponse response = new ReviewMapper().toResponse(review, "Suraj Gupta");
        assertEquals("Suraj G.", response.reviewerName());
    }
}
