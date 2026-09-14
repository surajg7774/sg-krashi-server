-- Corrections found during a follow-up primary-source verification pass on
-- the V34 schemes (requested after the first report conflated WebSearch's
-- own synthesis with genuine primary-source reads). Purely additive-style
-- UPDATE, doesn't touch V31/V33/V34.
--
-- SMAM: the CHC "40% up to Rs 250 lakh project cost" and "Farm Machinery
-- Bank 80% up to Rs 30 lakh" figures in the original seed were never
-- actually confirmed against a primary document — they came only from
-- WebSearch's own summary. Directly reading the real guidelines document
-- (Himachal Pradesh government's official mirror of "GUIDELINES OF SMAM -
-- 2020-21", Annexure-II(c) and (d)) confirms the farmer-purchase 40%/50%
-- split exactly, but shows CHC assistance is actually 40% capped in tiers
-- of Rs 4/10/16/24 lakh (for project costs up to Rs 10/25/40/60 lakh) - not
-- the Rs 250 lakh figure - and no Rs 30 lakh/80% Farm Machinery Bank figure
-- appears anywhere in that document at all. Since the CHC/FMB figures
-- can't be confirmed (and the one primary document read contradicts the
-- original number), the benefit text now states only what was actually
-- verified, without inventing specific unconfirmed CHC/FMB figures.
--
-- Namo Drone Didi: the original seed said "14,500 SHGs" - a real official
-- PIB document (static.pib.gov.in, Nov 2024), read directly, says 15,000.
-- Also clarifies the two periods mentioned across two different official
-- PIB documents aren't a contradiction: the scheme's overall outlay was
-- approved for 2023-24 to 2025-26 (per a June 2026 PIB report), while the
-- 15,000-SHG distribution target specifically spans 2024-25 to 2025-26
-- (per the Nov 2024 announcement).

UPDATE schemes
SET benefit = '40% of machine cost for general-category farmers, 50% for SC/ST, small/marginal farmers, women and North-Eastern-state beneficiaries (confirmed exactly against the official cost-norms annexure, including per-machine caps). Custom Hiring Centres and Farm Machinery Banks also get separate, tiered assistance under the same scheme, but the exact current caps for those weren''t independently confirmed here - check the official portal before relying on a specific figure for those.',
    updated_at = NOW(6)
WHERE name = 'SMAM (Sub-Mission on Agricultural Mechanization)';

UPDATE schemes
SET description = 'Provides subsidized agricultural drones to Women Self-Help Groups (SHGs), who are trained as drone pilots and then rent out drone spraying services (fertilizer/pesticide application) to farmers nearby — a farmer benefits by hiring an SHG''s drone service, not by receiving the subsidy directly. The scheme was approved with an overall outlay of ₹1,261 crore for 2023-24 to 2025-26.',
    eligibility = 'The drone subsidy goes to selected Women SHGs under DAY-NRLM (targeting 15,000 SHGs during 2024-25 to 2025-26); any farmer can hire a trained SHG''s drone service once one is operating in their area.',
    updated_at = NOW(6)
WHERE name = 'Namo Drone Didi';
