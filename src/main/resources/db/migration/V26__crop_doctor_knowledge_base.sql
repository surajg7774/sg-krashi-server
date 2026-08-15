-- V26__crop_doctor_knowledge_base.sql
-- AI Crop Doctor RAG (Retrieval-Augmented Generation): a small, curated
-- knowledge base retrieved by declared crop and injected into the Gemini
-- prompt as grounding context (see RetrievalService/GeminiAnalysisProvider).
--
-- Content is original text authored to summarize well-established, publicly
-- documented plant pathology and agricultural extension knowledge (the kind
-- of general crop-disease science consistently described by sources such as
-- ICAR and FAO) — not scraped or reproduced verbatim from any single
-- copyrighted publication. `source` on every row states this honestly rather
-- than fabricating a specific document citation.
--
-- crop is deliberately never NULL — retrieval is a plain crop-tag lookup
-- (Option 1: metadata-filtered, no embeddings — see RetrievalServiceImpl's
-- Javadoc), so an entry with no crop tag would never be reachable by it.
-- Crop names match this platform's own Crop Marketplace/Product Store
-- vocabulary (Chana, Moong, Masoor, Bajra) rather than their English
-- botanical names, since that's the vocabulary a user of this specific
-- platform is most likely to type as their declared crop.

CREATE TABLE knowledge_base_entries (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    crop VARCHAR(100) NOT NULL,
    topic VARCHAR(150) NOT NULL,
    title VARCHAR(200) NOT NULL,
    content TEXT NOT NULL,
    source VARCHAR(300) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE INDEX idx_knowledge_base_entries_crop ON knowledge_base_entries (crop);

INSERT INTO knowledge_base_entries (crop, topic, title, content, source, language, created_at, updated_at, is_active) VALUES

('Wheat', 'General Cultivation', 'Wheat: Common Diseases and General Crop Management',
'Wheat is affected most seriously by three rust diseases -- leaf (brown) rust, stem (black) rust, and stripe (yellow) rust -- along with powdery mildew, loose smut, and Karnal bunt. Rust diseases appear as small, raised, powdery pustules (orange-brown for leaf rust, black for stem rust, yellow stripes for stripe rust) on leaves and stems, and spread rapidly under humid conditions with moderate temperatures. Good practices include using rust-resistant/tolerant varieties, timely sowing to avoid peak disease-favorable weather, balanced nitrogen use (excess nitrogen increases susceptibility), field sanitation to remove volunteer plants that carry rust spores between seasons, and prompt fungicide application (e.g. propiconazole or tebuconazole-based products) at the first sign of pustules rather than waiting for widespread infection.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Wheat', 'Leaf Rust', 'Wheat Leaf Rust (Brown Rust)',
'Caused by the fungus Puccinia triticina. Symptoms are small, circular to oval, orange-brown powdery pustules scattered on the upper leaf surface, most common on the flag leaf and leaves just below it. Severe infection causes premature leaf drying, reduced grain filling, and yield loss. The disease spreads via windborne spores and is favored by moderate temperatures (15-22C) with dew or light rain. Management: sow resistant varieties where available, avoid excess nitrogen, and apply a triazole fungicide as soon as pustules are first observed, focusing coverage on the upper canopy.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Wheat', 'Karnal Bunt', 'Karnal Bunt of Wheat',
'Caused by the fungus Tilletia indica, this disease partially replaces individual wheat grains with a black, foul-smelling fungal mass, giving affected grain a fishy odor. Infection happens at flowering under cool, humid conditions and is often not visible until grain is threshed. Heavily infected lots can be rejected for trade due to quality/odor standards. Management relies on using certified disease-free seed, crop rotation with non-host crops, avoiding irrigation at flowering in known-affected areas, and seed treatment with recommended fungicides.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Rice', 'General Cultivation', 'Rice (Paddy): Common Diseases and General Crop Management',
'The most economically important rice diseases are blast, bacterial leaf blight, brown spot, and sheath blight. Blast and bacterial blight can each cause severe yield loss in a single season if conditions favor them. General management includes using resistant/tolerant varieties suited to the region, balanced fertilization (excess nitrogen strongly increases susceptibility to blast and bacterial blight), proper field drainage to avoid prolonged standing water that favors sheath blight, seed treatment before sowing, and field sanitation (removing infected stubble) to reduce carryover of fungal and bacterial inoculum between seasons.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Rice', 'Blast', 'Rice Blast',
'Caused by the fungus Magnaporthe oryzae (Pyricularia oryzae). Leaf blast appears as spindle-shaped grey-centered lesions with brown margins; neck blast (infection at the base of the panicle) causes the entire panicle to dry and turn white ("white head"), often the most damaging form since it directly destroys grain. Favored by high humidity, extended leaf wetness, and excess nitrogen. Management: use resistant varieties, avoid excess nitrogen especially late in the season, ensure good field drainage, and apply a systemic fungicide (e.g. tricyclazole) at first symptom onset, particularly protecting the panicle stage.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Rice', 'Bacterial Leaf Blight', 'Bacterial Leaf Blight of Rice',
'Caused by the bacterium Xanthomonas oryzae pv. oryzae. Symptoms begin as water-soaked streaks near leaf tips/margins that enlarge into yellow-to-white lesions with a wavy edge, eventually causing whole leaves to wilt and dry. Spreads rapidly through irrigation water, wind-driven rain, and leaf-to-leaf contact, especially after storms. Management: use resistant varieties, avoid clipping seedling leaf tips during transplanting (a common entry point), avoid excess nitrogen, maintain shallow rather than deep standing water, and avoid working in fields when foliage is wet to limit mechanical spread. Copper-based bactericides can help but resistant varieties are the most reliable control.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Maize', 'General Cultivation', 'Maize (Corn): Common Diseases, Pests, and General Crop Management',
'Maize is commonly affected by Turcicum (northern) leaf blight, common rust, and, in recent years across South Asia and Africa, the invasive fall armyworm pest. Turcicum leaf blight produces long, cigar-shaped grey-green lesions on leaves under humid conditions. Fall armyworm larvae feed inside the whorl, leaving characteristic ragged "windowpane" damage and visible frass (droppings). General management: crop rotation, resistant hybrids where available, timely sowing, field scouting during the whorl stage for armyworm egg masses/early larvae, and targeted insecticide or biological control (e.g. Bacillus thuringiensis-based products) rather than blanket spraying.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Maize', 'Turcicum Leaf Blight', 'Turcicum (Northern) Leaf Blight of Maize',
'Caused by the fungus Exserohilum turcicum. Lesions are long (several centimeters), elliptical, greyish-green to tan, running parallel to the leaf veins (cigar-shaped). Under favorable humid, moderate-temperature conditions, lesions coalesce and can blight entire leaves, reducing photosynthetic area and yield. Management: rotate with a non-host crop, use resistant hybrids, remove and destroy infected crop residue after harvest (the fungus survives on debris), and apply a fungicide if disease appears before tasseling in a susceptible field.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Maize', 'Fall Armyworm', 'Fall Armyworm in Maize',
'Fall armyworm (Spodoptera frugiperda) is a caterpillar pest, not a disease, but is one of the most damaging maize threats in India since its 2018 arrival. Young larvae scrape leaf surfaces (a windowpane appearance); older larvae bore into the whorl and cob, leaving ragged holes and visible sawdust-like frass. Regular field scouting during the vegetative/whorl stage is the most effective early-detection method. Management: hand-picking and destroying egg masses/young larvae in small plots, applying Bacillus thuringiensis-based biopesticides or recommended insecticides directly into the whorl (where larvae hide from surface sprays), and rotating insecticide classes to slow resistance development.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Cotton', 'General Cultivation', 'Cotton: Common Diseases, Pests, and General Crop Management',
'Cotton in India faces both diseases (bacterial blight, Fusarium wilt) and major pests (bollworms, whitefly-transmitted leaf curl virus). Bollworms (American, pink, and spotted bollworm) bore into squares and bolls, causing direct yield and quality loss; pink bollworm larvae specifically feed inside developing bolls, often undetected until the boll is opened. General management: use recommended Bt or resistant varieties where appropriate for local pest pressure, monitor with pheromone traps for early bollworm detection, avoid continuous cotton monoculture (rotate with a non-host crop to break pest/disease cycles), and remove/destroy crop residue after harvest since several pests and pathogens overwinter in leftover plant material.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Cotton', 'Bollworm', 'Cotton Bollworm (including Pink Bollworm)',
'Bollworms are caterpillar pests that bore into cotton squares (flower buds) and bolls. Pink bollworm (Pectinophora gossypiella) larvae enter young bolls through a tiny, hard-to-see hole and feed inside on developing seed and lint, causing stained/damaged lint that is often only discovered when the boll is opened. American bollworm (Helicoverpa armigera) is more polyphagous and visibly damages squares and bolls from outside first. Management: pheromone traps for early warning, timely destruction of crop residue and volunteer plants (pink bollworm overwinters in old bolls/plant debris), avoiding late-season extra flushes of cotton that give the pest more generations, and targeted insecticide application timed to trap catches rather than a fixed calendar.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Cotton', 'Bacterial Blight and Wilt', 'Cotton Bacterial Blight and Fusarium Wilt',
'Bacterial blight (Xanthomonas citri pv. malvacearum) causes angular, water-soaked leaf spots that turn dark and necrotic, and can also cause "black arm" lesions on stems and boll rot in humid weather; it spreads via infected seed, rain-splash, and wind-driven rain. Fusarium wilt (Fusarium oxysporum f. sp. vasinfectum) is a soil-borne fungus causing yellowing, wilting, and eventual death of plants, often first appearing as one-sided wilting of a plant; it persists in soil for years and cannot be easily eliminated once established. Management: use certified, disease-free/treated seed, resistant varieties where available, crop rotation with non-host crops for wilt-affected fields, and avoiding field work when foliage is wet (for bacterial blight specifically).',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Chana', 'General Cultivation', 'Chickpea (Chana): Common Diseases and General Crop Management',
'Chickpea''s two most damaging diseases are Fusarium wilt and Ascochyta blight, which can each cause total crop loss in a susceptible field under favorable conditions. Both are seed- and soil/residue-borne, so clean seed and field sanitation are the foundation of management. General practices: use certified, disease-free seed (or seed treated with a recommended fungicide), grow wilt/blight-tolerant varieties where locally available, rotate with a non-host crop (cereals) for at least 2-3 years in fields with a history of wilt, and avoid dense sowing/excess irrigation that keeps the canopy humid, which favors blight spread.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Chana', 'Fusarium Wilt', 'Fusarium Wilt of Chickpea',
'Caused by the soil-borne fungus Fusarium oxysporum f. sp. ciceris. Affected plants wilt suddenly, often starting with the whole plant drooping while still green, followed by drying; a diagnostic sign is dark discoloration of the vascular tissue visible when the lower stem is split open. The fungus survives in soil for many years, so once a field is infested, complete elimination is impractical. Management: sow resistant/tolerant varieties, avoid sowing chickpea after chickpea in the same field for several seasons, treat seed with a recommended fungicide before sowing, and avoid waterlogging, which worsens wilt severity.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Chana', 'Ascochyta Blight', 'Ascochyta Blight of Chickpea',
'Caused by the fungus Ascochyta rabiei. Symptoms are round to irregular brown lesions with concentric rings on leaves, stems, and pods, often with tiny dark fungal fruiting bodies visible in the lesion center; severe infection can girdle and snap stems. Spreads rapidly in cool, wet, humid weather via rain-splashed spores, and can travel long distances on infected seed. Management: use certified disease-free seed, avoid sowing in fields with a recent blight history, apply a protectant fungicide at first symptom appearance (especially ahead of forecast wet weather), and avoid working in wet fields to limit mechanical spread.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Moong', 'Yellow Mosaic Disease', 'Moong (Green Gram): Yellow Mosaic Disease and General Crop Management',
'The most significant disease of moong (and related mungbeans/urad) across India is Mungbean Yellow Mosaic Disease (MYMV), a virus transmitted by whitefly. Infected plants show irregular yellow-and-green mosaic patches on leaves, stunted growth, and reduced pod formation; severely infected plants may produce almost no usable pods. Since the virus itself has no direct cure once a plant is infected, management focuses on prevention: growing tolerant/resistant varieties where available, early sowing to help plants mature before peak whitefly season, controlling whitefly populations (yellow sticky traps, recommended insecticides), and removing severely infected plants early to reduce the source of further whitefly-transmitted spread within the field.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Masoor', 'Rust and Wilt', 'Masoor (Lentil): Rust, Wilt, and General Crop Management',
'Lentil is mainly threatened by rust (Uromyces viciae-fabae) and Fusarium wilt. Rust appears as small, reddish-brown powdery pustules on leaves and stems, spreading fast in humid weather and causing premature leaf drop if severe. Wilt causes sudden yellowing and drying of plants, often in patches within a field, with the fungus persisting in soil for years. General management: use certified seed and resistant/tolerant varieties where available, avoid dense sowing (better airflow reduces humidity around plants), rotate with cereals in wilt-affected fields, and apply a fungicide at the first sign of rust pustules rather than after they have spread across the canopy.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Bajra', 'Downy Mildew', 'Bajra (Pearl Millet): Downy Mildew (Green Ear Disease)',
'Downy mildew, caused by the fungus-like pathogen Sclerospora graminicola, is pearl millet''s most significant disease in India. It causes "green ear" symptoms -- the normal grain-bearing panicle is transformed into a mass of leafy green tissue instead of producing grain -- along with pale yellow streaking on leaves and stunted growth in severely infected plants. The pathogen survives in soil and on infected seed/plant debris between seasons, and spreads further via airborne spores from infected plants during the growing season. Management: use resistant hybrids (widely available and the most effective single measure), treat seed with a recommended fungicide before sowing, rogue out (remove and destroy) infected plants showing green ear symptoms as soon as spotted, and avoid continuous pearl millet monoculture in the same field.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Tomato', 'General Cultivation', 'Tomato: Common Diseases and General Crop Management',
'Tomato is affected by several major diseases: early blight, late blight, and tomato leaf curl virus (transmitted by whitefly) are among the most damaging in Indian growing conditions. Early and late blight are fungal/oomycete diseases favored by humid weather, while leaf curl virus spreads via whitefly and has no direct cure once a plant is infected. General management: use disease-tolerant varieties where available, stake/prune plants for good airflow (reduces leaf wetness duration), avoid overhead irrigation late in the day (wet foliage overnight favors fungal disease), rotate with non-solanaceous crops (avoid following potato, brinjal, or chilli), and control whitefly populations early to limit virus spread.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Tomato', 'Early Blight', 'Early Blight of Tomato',
'Caused by the fungus Alternaria solani. Symptoms are dark brown spots with concentric rings (a target or bullseye pattern) that appear first on older, lower leaves and expand outward; severely affected leaves yellow and drop, and lesions can also appear on stems and fruit near the calyx. Favored by warm temperatures with alternating wet and dry periods. Management: remove and destroy infected lower leaves/debris, avoid overhead watering, mulch to reduce soil splash onto lower leaves (a common infection route), and apply a protectant fungicide (e.g. mancozeb or chlorothalonil-based) starting before symptoms appear if the disease has a history in the field.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Tomato', 'Late Blight', 'Late Blight of Tomato',
'Caused by the oomycete Phytophthora infestans (the same pathogen responsible for the 19th-century Irish potato famine, which also attacks tomato). Symptoms are irregular, water-soaked, dark green-to-brown lesions on leaves that expand rapidly, often with a pale fuzzy fungal growth visible on the underside in humid conditions; stems and fruit can also develop dark, greasy-looking lesions. Spreads extremely quickly in cool, wet, humid weather and can destroy a field within days if unmanaged. Management: use resistant varieties where available, ensure good field drainage and airflow, avoid overhead irrigation, remove and destroy infected plant material immediately (do not compost), and apply a systemic fungicide preventatively during forecast periods of cool wet weather rather than waiting for visible symptoms.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Tomato', 'Leaf Curl Virus', 'Tomato Leaf Curl Virus',
'A whitefly-transmitted virus causing upward curling and crinkling of leaves, yellowing of leaf margins, stunted overall growth, and significantly reduced fruit set -- plants infected while young often bear little to no fruit. There is no direct cure once a plant is infected; management is entirely preventive. Practices: use tolerant/resistant varieties where available, raise seedlings under insect-proof nylon net covering before transplanting (protects the most vulnerable early growth stage), control whitefly populations with yellow sticky traps and recommended insecticides, and remove and destroy infected plants promptly to reduce the whitefly-accessible source of further spread within the field.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Potato', 'General Cultivation', 'Potato: Common Diseases and General Crop Management',
'Potato''s most historically and economically significant disease is late blight, the same pathogen responsible for the Irish potato famine of the 1840s; early blight is also common. Both are favored by humid conditions and can spread rapidly through a field once established. General management: plant certified disease-free seed tubers (the single most important preventive step, since both diseases can be seed-borne), practice crop rotation (avoid planting potato or other solanaceous crops in the same field for 2-3 years), hill soil around the base of plants to protect developing tubers from spores washed down from infected foliage, and monitor weather forecasts for cool, wet conditions that favor late blight outbreaks so fungicide can be applied preventively.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Potato', 'Late Blight', 'Late Blight of Potato',
'Caused by the oomycete Phytophthora infestans. Symptoms begin as small, water-soaked, pale green-to-black lesions on leaf edges/tips that expand rapidly under cool, humid conditions, often with white fungal growth visible on the leaf underside in early morning humidity. Infected tubers develop a reddish-brown, granular dry rot beneath the skin. This disease can destroy an entire unmanaged field within one to two weeks under favorable weather -- it is the same disease responsible for the 1840s Irish famine. Management: use certified disease-free seed tubers, ensure good field drainage and airflow, hill soil to protect tubers from spore wash-down, remove and destroy any infected plants/tubers immediately, and apply a protectant or systemic fungicide preventively ahead of forecast cool, wet weather.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Potato', 'Early Blight', 'Early Blight of Potato',
'Caused by the fungus Alternaria solani. Symptoms are dark brown spots with concentric target-like rings, appearing first on older/lower leaves and expanding with continued humid weather; severe infection causes premature defoliation and reduced tuber size. Generally less destructive and slower-spreading than late blight, but can still meaningfully reduce yield if unmanaged, especially on stressed or nutrient-deficient plants. Management: maintain balanced plant nutrition (nutrient-stressed plants are more susceptible), remove infected lower leaves and debris, avoid overhead irrigation, and apply a protectant fungicide if disease pressure is high in the region.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Onion', 'Purple Blotch', 'Onion: Purple Blotch and General Crop Management',
'Purple blotch (caused by the fungus Alternaria porri) is one of onion''s most common foliar diseases, appearing as small water-soaked spots that enlarge into purplish-brown lesions with concentric zoning, often surrounded by a yellow halo; severe infection causes leaf tips to die back and can reduce bulb size. Favored by warm, humid weather with frequent rain or heavy dew. Management: avoid overhead irrigation (drip/furrow irrigation reduces leaf wetness), ensure adequate plant spacing for airflow, remove and destroy infected leaf debris after harvest, rotate with a non-allium crop, and apply a protectant fungicide at first symptom onset during humid weather.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Chilli', 'Anthracnose', 'Green Chilli: Anthracnose (Fruit Rot) and General Crop Management',
'Anthracnose, caused by Colletotrichum species, is chilli''s most damaging fruit disease, appearing as sunken, circular, dark lesions on ripening fruit, often with concentric rings of dark spore masses visible in the center; heavily infected fruit rot and drop before harvest. The fungus survives on infected seed and crop debris between seasons. Chilli leaf curl virus (whitefly-transmitted) is also common, causing upward leaf curling, stunted growth, and reduced yield, with no direct cure once infected. General management: use certified disease-free seed, avoid overhead irrigation late in the day, remove and destroy infected fruit and debris promptly (do not leave on the ground), control whitefly populations early to limit leaf curl virus spread, and rotate with a non-solanaceous crop.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE),

('Cauliflower', 'Black Rot', 'Cauliflower: Black Rot and General Crop Management',
'Black rot, caused by the bacterium Xanthomonas campestris pv. campestris, is cauliflower''s (and other cole crops'') most serious disease, producing characteristic V-shaped yellow lesions starting at leaf margins that progress inward toward the leaf vein, with veins often turning black; infection can spread systemically and stunt or kill young plants. Spreads via infected seed, rain-splash, and contaminated tools/water. Downy mildew (a separate, fungus-like disease) causes pale yellow patches on the upper leaf surface with grey-white fuzzy growth underneath, favored by cool, humid weather. Management: use certified disease-free seed, avoid working in wet fields (reduces mechanical spread of black rot bacteria), rotate with non-cole crops for at least 2 years, remove and destroy infected debris after harvest, and ensure good field drainage and airflow to reduce the humid conditions both diseases favor.',
'Original summary based on publicly documented plant pathology/agricultural extension knowledge (ICAR/FAO-aligned); not reproduced from a specific copyrighted source.', 'en', NOW(6), NOW(6), TRUE);
