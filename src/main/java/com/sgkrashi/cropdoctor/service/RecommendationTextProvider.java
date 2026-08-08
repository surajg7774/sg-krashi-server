package com.sgkrashi.cropdoctor.service;

import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Static, educational recommendation copy keyed by the AI service's raw
 * class label ({@code Crop___Disease}, from the Python service's
 * {@code class_label} field — see AiPredictionResponse). Deliberately never
 * phrased as a guaranteed treatment prescription (Section 8 of the feature
 * spec): every entry points the user toward expert confirmation rather than
 * naming a specific product or dose.
 */
@Component
public class RecommendationTextProvider {

    private static final String HEALTHY_TEXT =
            "No signs of disease were detected in this image. Keep monitoring the plant regularly and "
                    + "maintain good watering, sunlight, and soil practices.";

    private static final String FALLBACK_TEXT =
            "This may indicate a plant health issue. Consider consulting a local agricultural extension "
                    + "office or plant pathologist for a confirmed diagnosis and treatment guidance.";

    private static final String UNCERTAINTY_PREFIX =
            "This result is uncertain — try a clearer, closer photo of the affected leaf, or consult an "
                    + "agriculture expert for confirmation. ";

    private static final Map<String, String> RECOMMENDATIONS = Map.ofEntries(
            Map.entry("Apple___Apple_scab",
                    "May indicate apple scab, a fungal disease. Consider removing fallen leaves that harbor "
                            + "the fungus over winter and consulting a local agricultural extension office about "
                            + "resistant varieties and timing of protective sprays."),
            Map.entry("Apple___Black_rot",
                    "May indicate black rot. Consider pruning out visibly affected branches and consulting a "
                            + "local agricultural extension office for guidance on sanitation and treatment."),
            Map.entry("Apple___Cedar_apple_rust",
                    "May indicate cedar apple rust, which requires a nearby juniper/cedar host to spread. "
                            + "Consider consulting a local agricultural extension office about resistant varieties "
                            + "and control timing."),
            Map.entry("Cherry___Powdery_mildew",
                    "May indicate powdery mildew. Consider improving air circulation around the plant and "
                            + "consulting a local agricultural extension office for treatment guidance."),
            Map.entry("Corn___Cercospora_leaf_spot",
                    "May indicate Cercospora leaf spot (gray leaf spot). Consider crop rotation and residue "
                            + "management, and consult a local agricultural extension office for confirmation and "
                            + "treatment guidance."),
            Map.entry("Corn___Common_rust",
                    "May indicate common rust. Consider consulting a local agricultural extension office about "
                            + "resistant hybrids and whether treatment is warranted at this stage."),
            Map.entry("Corn___Northern_Leaf_Blight",
                    "May indicate Northern leaf blight. Consider crop rotation and residue management, and "
                            + "consult a local agricultural extension office for confirmation and treatment guidance."),
            Map.entry("Grape___Black_rot",
                    "May indicate black rot. Consider removing mummified fruit and affected leaves, and "
                            + "consult a local agricultural extension office for treatment guidance."),
            Map.entry("Grape___Esca_(Black_Measles)",
                    "May indicate Esca (black measles), a trunk disease with no reliable cure once established. "
                            + "Consider consulting a local agricultural extension office or plant pathologist for "
                            + "confirmation and management options."),
            Map.entry("Grape___Leaf_blight",
                    "May indicate leaf blight (Isariopsis leaf spot). Consider consulting a local agricultural "
                            + "extension office for confirmation and treatment guidance."),
            Map.entry("Orange___Haunglongbing",
                    "May indicate citrus greening (Huanglongbing), a serious disease with no cure — early "
                            + "reporting matters. Strongly consider contacting a local agricultural extension "
                            + "office promptly for confirmation."),
            Map.entry("Peach___Bacterial_spot",
                    "May indicate bacterial spot. Consider consulting a local agricultural extension office "
                            + "for confirmation and treatment guidance."),
            Map.entry("Pepper___Bacterial_spot",
                    "May indicate bacterial spot. Consider avoiding overhead watering and consulting a local "
                            + "agricultural extension office for treatment guidance."),
            Map.entry("Potato___Early_blight",
                    "May indicate early blight. Consider crop rotation and removing affected foliage, and "
                            + "consult a local agricultural extension office for treatment guidance."),
            Map.entry("Potato___Late_blight",
                    "May indicate late blight, which can spread quickly in humid conditions. Consider "
                            + "consulting a local agricultural extension office promptly for confirmation and "
                            + "treatment guidance."),
            Map.entry("Squash___Powdery_mildew",
                    "May indicate powdery mildew. Consider improving air circulation and consulting a local "
                            + "agricultural extension office for treatment guidance."),
            Map.entry("Strawberry___Leaf_scorch",
                    "May indicate leaf scorch. Consider removing heavily affected leaves after harvest and "
                            + "consulting a local agricultural extension office for treatment guidance."),
            Map.entry("Tomato___Bacterial_spot",
                    "May indicate bacterial spot. Consider avoiding overhead watering and consulting a local "
                            + "agricultural extension office for treatment guidance."),
            Map.entry("Tomato___Early_blight",
                    "May indicate early blight. Consider crop rotation and removing affected lower leaves, and "
                            + "consult a local agricultural extension office for treatment guidance."),
            Map.entry("Tomato___Late_blight",
                    "May indicate late blight, which can spread quickly in humid conditions. Consider "
                            + "consulting a local agricultural extension office promptly for confirmation and "
                            + "treatment guidance."),
            Map.entry("Tomato___Leaf_Mold",
                    "May indicate leaf mold. Consider improving ventilation (especially in greenhouses/tunnels) "
                            + "and consulting a local agricultural extension office for treatment guidance."),
            Map.entry("Tomato___Septoria_leaf_spot",
                    "May indicate Septoria leaf spot. Consider removing affected lower leaves and consulting a "
                            + "local agricultural extension office for treatment guidance."),
            Map.entry("Tomato___Spider_mites",
                    "May indicate a spider mite infestation rather than a disease. Consider consulting a local "
                            + "agricultural extension office for identification and control guidance."),
            Map.entry("Tomato___Target_Spot",
                    "May indicate target spot. Consider consulting a local agricultural extension office for "
                            + "confirmation and treatment guidance."),
            Map.entry("Tomato___Yellow_Leaf_Curl_Virus",
                    "May indicate Tomato yellow leaf curl virus, typically spread by whiteflies. Consider "
                            + "consulting a local agricultural extension office about whitefly control and "
                            + "resistant varieties."),
            Map.entry("Tomato___Tomato_mosaic_virus",
                    "May indicate Tomato mosaic virus. There is no cure once infected — consider consulting a "
                            + "local agricultural extension office about removing affected plants and preventing "
                            + "spread to healthy ones.")
    );

    public String recommendationFor(String classLabel, boolean isHealthy) {
        if (isHealthy) {
            return HEALTHY_TEXT;
        }
        return RECOMMENDATIONS.getOrDefault(classLabel, FALLBACK_TEXT);
    }

    public String applyUncertaintyCaveat(String recommendation, boolean isUncertain) {
        if (!isUncertain) {
            return recommendation;
        }
        return UNCERTAINTY_PREFIX + recommendation;
    }
}
