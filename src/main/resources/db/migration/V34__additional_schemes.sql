-- Adds 8 more major central schemes to the Scheme Hub (bringing the total to
-- 12), verified against official sources with the same rigor as V31/V33 —
-- see the accompanying report for exactly what was checked and where. Purely
-- additive (only INSERT, no UPDATE/ALTER on existing rows), same safe pattern
-- as V33, so this never touches an already-applied migration's content.
--
-- Notable finding while researching: MSP is commonly described online as
-- covering "23 crops", but the government's own current PIB figure is 22
-- mandated crops (14 Kharif, 6 Rabi/other, plus derived MSPs for toria and
-- de-husked coconut off rapeseed-mustard/copra, which is likely where the
-- "23" figure comes from). Seeded with the officially-stated 22, not 23.
--
-- Also found the official CACP domain has moved: cacp.dacnet.nic.in (the
-- commonly-cited URL) no longer resolves at all (DNS failure) — the live
-- site is cacp.da.gov.in, specifically cacp.da.gov.in/Home/MSP for current
-- per-crop rates, which is what's used below.

INSERT INTO schemes (name, category, description, eligibility, benefit, official_link, state_scope, sort_order, created_at, updated_at, is_active) VALUES

('SMAM (Sub-Mission on Agricultural Mechanization)', 'EQUIPMENT_SUBSIDY',
 'The central government''s nationwide farm-machinery subsidy — available to farmers in every state, unlike the Madhya Pradesh–specific Krishi Yantra Anudan Yojana listed separately below. Covers tractors, power tillers, harvesters and other implements, plus support for Custom Hiring Centres and Farm Machinery Banks that rent out equipment to farmers who can''t afford to buy their own.',
 'Any farmer buying eligible farm machinery, or an entrepreneur/FPO/SHG setting up a Custom Hiring Centre or Farm Machinery Bank; subsidy tier depends on farmer category.',
 '40% of machine cost for general-category farmers; 50% for SC/ST, small/marginal farmers and farmers in North-Eastern states. Custom Hiring Centres can get 40% assistance on eligible projects up to ₹250 lakh; Farm Machinery Banks up to 80% on projects up to ₹30 lakh.',
 'https://agrimachinery.nic.in/', NULL, 45, NOW(6), NOW(6), TRUE),

('PM-KUSUM (Pradhan Mantri Kisan Urja Suraksha evam Utthaan Mahabhiyaan)', 'IRRIGATION',
 'Solar-powered irrigation support in three components: grid-connected solar power plants on farmland (Component A), standalone solar irrigation pumps for areas without grid power (Component B), and solarizing an existing grid-connected pump (Component C) — cutting diesel/electricity costs and letting farmers sell surplus power back to DISCOMs.',
 'Farmers wanting a standalone solar pump (up to 7.5 HP) or wanting to solarize an existing grid-connected pump; applied for through the state renewable energy nodal agency.',
 '30% Central Financial Assistance plus 30% state subsidy, leaving a 40% farmer share (50% CFA and only 20% farmer share in North-Eastern/hill/island states) — bank financing can reduce the farmer''s upfront payment to as little as 10%. Several states add their own top-up subsidy on top of this central structure.',
 'https://pmkusum.mnre.gov.in/', NULL, 50, NOW(6), NOW(6), TRUE),

('PMKSY – Per Drop More Crop (Micro Irrigation)', 'IRRIGATION',
 'Subsidy for drip and sprinkler irrigation systems, to raise water-use efficiency and reduce over-extraction — covers up to 5 hectares per beneficiary.',
 'Any farmer installing drip/sprinkler irrigation on land they own or lease, up to 5 hectares; small and marginal farmers get the higher subsidy tier.',
 '55% of the unit cost (including GST) for small and marginal farmers, 45% for other farmers, funded by Centre and State in a 60:40 ratio (90:10 in North Eastern and Himalayan states; 100% Central funding in Union Territories).',
 'https://pmksy.gov.in/', NULL, 60, NOW(6), NOW(6), TRUE),

('Soil Health Card Scheme', 'ADVISORY',
 'Free soil testing that gives every farmer a printed report of their land''s nutrient status, with specific fertilizer and soil-treatment recommendations for their soil.',
 'Every farmer with cultivable land; a card is issued for each land holding once every 2 years through the local agriculture department''s soil testing lab.',
 'A free report testing 12 parameters — Nitrogen, Phosphorus, Potassium, Sulphur (macro-nutrients); Zinc, Iron, Copper, Manganese, Boron (micro-nutrients); and pH, Electrical Conductivity, Organic Carbon — with fertilizer, bio-fertilizer and soil-treatment guidance. Over 25 crore cards distributed nationwide as of mid-2025.',
 'https://soilhealth.dac.gov.in/', NULL, 70, NOW(6), NOW(6), TRUE),

('e-NAM (National Agriculture Market)', 'MARKET_ACCESS',
 'An online trading platform connecting mandis (APMCs) across India into one unified digital marketplace, so a farmer can get bids from buyers beyond their local market — the same real-time price data our own Mandi Price Tracker is built on.',
 'Any farmer can register for free, either directly on the e-NAM portal or through their local integrated mandi.',
 'Transparent, competitive online bidding across 1,656 mandis in 23 states and 4 Union Territories (as of March 2026); free for farmers to list produce. Over 1.80 crore farmers and 2.73 lakh traders are registered nationwide.',
 'https://enam.gov.in/web/', NULL, 80, NOW(6), NOW(6), TRUE),

('PM-KMY (Pradhan Mantri Kisan Maandhan Yojana)', 'PENSION',
 'A voluntary pension scheme for small and marginal farmers, guaranteeing a monthly pension after age 60.',
 'Small and marginal farmers aged 18–40 with combined land holding up to 2 hectares. Excludes farmers already covered by other statutory social security/pension schemes (EPFO, ESIC, NPS), income-tax payers, and current/former holders of specified public offices.',
 'A monthly pension of ₹3,000 after turning 60, funded by a matching 1:1 government contribution. Monthly contribution during working years ranges from ₹55 (joining at 18) to ₹200 (joining at 40), and can be paid directly out of PM-KISAN instalments.',
 'https://maandhan.in/', NULL, 100, NOW(6), NOW(6), TRUE),

('MSP (Minimum Support Price)', 'PRICE_SUPPORT',
 'Not an application-based scheme like the others here — MSP is the guaranteed minimum price the government commits to pay for certain crops, protecting farmers from distress sales when market prices fall. Rates are set separately per crop each season and change over time, so this entry deliberately doesn''t quote a fixed benefit figure.',
 'Any farmer selling an MSP-notified crop through designated government procurement centres for that crop and season.',
 'Currently covers 22 government-mandated crops (14 Kharif, 6 Rabi/other, plus derived rates for toria and de-husked coconut) — often rounded to "23" elsewhere, but 22 is the government''s own current figure. Rates are revised each Kharif and Rabi season and vary by crop — see the official CACP page for today''s per-crop rates rather than a number here.',
 'https://cacp.da.gov.in/Home/MSP', NULL, 110, NOW(6), NOW(6), TRUE),

('Namo Drone Didi', 'RENTAL_SERVICE',
 'Provides subsidized agricultural drones to Women Self-Help Groups (SHGs), who are trained as drone pilots and then rent out drone spraying services (fertilizer/pesticide application) to farmers nearby — a farmer benefits by hiring an SHG''s drone service, not by receiving the subsidy directly.',
 'The drone subsidy goes to selected Women SHGs under DAY-NRLM (2024-25 to 2025-26, targeting 14,500 SHGs nationwide); any farmer can hire a trained SHG''s drone service once one is operating in their area.',
 'Central Financial Assistance of 80% of the drone package cost, up to ₹8 lakh, for the selected SHG; the remaining cost is available as a loan at 3% interest subvention. One SHG member is trained as a certified drone pilot, another as a drone assistant/repair technician.',
 'https://www.pib.gov.in/PressReleasePage.aspx?PRID=2070029', NULL, 120, NOW(6), NOW(6), TRUE);
