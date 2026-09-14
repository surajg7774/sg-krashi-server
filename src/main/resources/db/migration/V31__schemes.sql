-- Government scheme/subsidy reference data (a dedicated table, not a new
-- ContentBlockType — see V31's accompanying design note: CMS's loose-JSON
-- design exists specifically to avoid a table per one-off marketing block,
-- and AdminCmsPage.tsx is hardcoded to a 2-way Banner/Testimonial branch, so
-- a third type is nontrivial frontend-admin work for structured domain data
-- (category/state filtering) that CMS was never meant to serve).
CREATE TABLE schemes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    category VARCHAR(40) NOT NULL,
    description TEXT NOT NULL,
    eligibility TEXT NOT NULL,
    benefit TEXT NOT NULL,
    official_link VARCHAR(500) NOT NULL,
    -- NULL = national scheme. Non-null names a specific state the scheme is
    -- restricted to (e.g. "Madhya Pradesh" for the MP-only equipment
    -- subsidy) — a UI filter/badge distinguishing "applies everywhere" from
    -- "applies only in this state", not a hard visibility restriction.
    state_scope VARCHAR(100) NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Seed content — figures verified via web search against multiple current
-- (2026) sources, not a primary .gov.in read; flagged for a pre-launch
-- accuracy check against the actual government portals before this is
-- trusted as final (same honesty standard as everywhere else in this app).
INSERT INTO schemes (name, category, description, eligibility, benefit, official_link, state_scope, sort_order, created_at, updated_at, is_active) VALUES
('PM-KISAN (Pradhan Mantri Kisan Samman Nidhi)', 'INCOME_SUPPORT',
 'Direct income support paid straight to farmers'' bank accounts via DBT, in three equal instalments through the year.',
 'Indian citizen; owns cultivable agricultural land in their own name. Tenant farmers who do not hold land in their own name are not covered.',
 '₹6,000 per year, paid as three instalments of ₹2,000 each (roughly April–July, August–November, and December–March).',
 'https://pmkisan.gov.in', NULL, 10, NOW(6), NOW(6), TRUE),

('PMFBY (Pradhan Mantri Fasal Bima Yojana)', 'INSURANCE',
 'Crop insurance covering yield losses from drought, flood, pests, disease, cyclone and other notified natural risks, for notified crops in notified areas.',
 'Landowners, tenant farmers and sharecroppers growing a notified crop in a notified area. Voluntary for all farmers (loanee and non-loanee alike).',
 'Farmer pays a capped premium — about 2% of sum insured for Kharif crops, 1.5% for Rabi crops, and 5% for commercial/horticulture crops — with the government subsidizing the remainder of the actuarial premium.',
 'https://pmfby.gov.in', NULL, 20, NOW(6), NOW(6), TRUE),

('Kisan Credit Card (KCC)', 'CREDIT',
 'A revolving credit line for farming needs (seed, fertilizer, equipment and allied activities) so a farmer isn''t re-applying for a fresh loan every season.',
 'Owner-cultivators, tenant farmers, sharecroppers and those in allied activities (dairy, fisheries); requires proof of landholding/cropping pattern from revenue authorities.',
 'Collateral-free loans up to ₹2 lakh; effective interest around 4% p.a. for prompt repayment (a 7% base rate reduced by a 2% government interest subvention and a further 3% prompt-repayment incentive).',
 'https://www.myscheme.gov.in/schemes/kcc', NULL, 30, NOW(6), NOW(6), TRUE),

('MP Krishi Yantra Anudan Yojana', 'EQUIPMENT_SUBSIDY',
 'Madhya Pradesh''s farm-machinery subsidy on tractors, power tillers, combine harvesters, threshers, rotavators, reaper-binders, paddy transplanters, sprayers, laser levellers and 40+ other implements. Applications go through a lottery-based allocation on the state''s DBT portal, in seasonal windows.',
 'Farmers registered on the Madhya Pradesh e-Krishi Yantra Anudan / DBT portal; higher subsidy tier for SC/ST, women, and small/marginal farmers.',
 '40% subsidy for general-category farmers (maximum ₹1.5 lakh); 50% subsidy for SC/ST, women, and small/marginal farmers (maximum ₹2 lakh).',
 'https://dbt.mpdage.org', 'Madhya Pradesh', 40, NOW(6), NOW(6), TRUE);
