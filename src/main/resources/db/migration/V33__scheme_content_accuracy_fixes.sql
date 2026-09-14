-- Primary-source verification pass on the 4 seeded schemes (V31), per the
-- task that flagged the original secondary-sourced content for a pre-launch
-- accuracy check. Findings:
--
-- PM-KISAN: CONFIRMED exact match against pmkisan.gov.in directly (₹6,000/yr,
-- 3 installments of ₹2,000). No change.
--
-- PMFBY: premium rates CONFIRMED exact match against the official operational
-- guidelines PDF (pmfby.gov.in/pdf/Revised_Operational_Guidelines.pdf,
-- Section 13.1, Table 1 — 2.0% Kharif, 1.5% Rabi, 5% commercial/horticultural).
-- Eligibility text was WRONG: the same guidelines (Section 8, "Compulsory
-- Component"/"Voluntary Component") state coverage is compulsory for loanee
-- farmers (anyone with a sanctioned seasonal agricultural loan) and only
-- voluntary for non-loanee farmers — the seed's "voluntary for all farmers
-- (loanee and non-loanee alike)" directly contradicted this. Fixed below.
--
-- KCC: benefit text had two real errors, found by cross-checking the Union
-- Budget 2025-26 speech and the Cabinet's May 2025 press release continuing
-- the Modified Interest Subvention Scheme (MISS) for FY 2025-26 (both
-- corroborated across pib.gov.in, pmindia.gov.in, and ddnews.gov.in):
--   1. The interest subvention is 1.5%, not 2% as originally seeded.
--   2. The seed conflated two DIFFERENT loan ceilings into one: the
--      collateral-free ceiling (₹2 lakh, unchanged) and the MISS interest-
--      subvention ceiling that the ~4% effective rate actually applies up
--      to (raised from ₹3 lakh to ₹5 lakh in Budget 2025-26). The original
--      text implied the 4% rate itself was capped at ₹2 lakh, which is
--      incorrect — that's the collateral-free limit, a separate figure.
--   The 7%->4% math was also imprecise (the 7% concessional rate already
--   reflects the 1.5% subvention paid to lenders; a further 3% Prompt
--   Repayment Incentive is what brings a farmer's own payable rate to 4%,
--   not a second subtraction from 7%).
--
-- MP Krishi Yantra Anudan Yojana: dbt.mpdage.org itself is a JS-rendered
-- portal with no fetchable static content, so this could not be checked
-- against a single primary page directly — but the 40%/50% rates and
-- ₹1.5 lakh/₹2 lakh caps were corroborated identically across multiple
-- independent 2026 sources. No change, but flagging this one as
-- secondary-sourced-and-corroborated rather than primary-confirmed like
-- the other three.

UPDATE schemes
SET eligibility = 'Landowners, tenant farmers and sharecroppers growing a notified crop in a notified area. Compulsory for loanee farmers (anyone with a sanctioned seasonal agricultural/KCC loan for the notified crop); voluntary for non-loanee farmers.',
    updated_at = NOW(6)
WHERE name = 'PMFBY (Pradhan Mantri Fasal Bima Yojana)';

UPDATE schemes
SET benefit = 'Short-term crop loans at a concessional 7% p.a. (1.5% government interest subvention to lenders), further reduced to an effective 4% p.a. for farmers who repay on time (3% Prompt Repayment Incentive) — this subvention applies to loans up to ₹5 lakh (raised from ₹3 lakh in Budget 2025-26). The first ₹2 lakh of any KCC loan is collateral-free regardless of repayment history.',
    updated_at = NOW(6)
WHERE name = 'Kisan Credit Card (KCC)';
