-- Live re-verification of the MP Krishi Yantra Anudan Yojana entry, done by
-- driving a real browser through dbt.mpdage.org's actual redirect target
-- (farmer.mpdage.mp.gov.in) and its live Subsidy Calculator tool, since
-- plain WebFetch could only ever see this portal's JS-shell HTML with no
-- static content. Two genuine, current (13 Sep 2026) findings:
--
-- 1. This portal isn't one single named scheme with one flat rate - it's a
--    unified DBT delivery platform that routes different implements through
--    different underlying central schemes. Tested live in the Subsidy
--    Calculator: a Ridger ("रिजर") is funded via "राष्ट्रीय कृषि विकास योजना
--    (RKVY) सामान्य-डी.पी.आर" at 40% for General-category farmers and 50%
--    for SC-category farmers (matching the split already found in the
--    primary SMAM guidelines document) - but a Happy Seeder ("हैप्पी सीडर"),
--    one of the implements in the live application window (27 Jul-04 Aug
--    2026), is funded via "SMAM अंडर RKVY कैफेटेरिया" at a flat 50% for
--    EVERY category (General and SC alike) - no category-based split at all
--    for that specific implement. The 40%/50% general-vs-reserved split is
--    real and current, but it does not apply uniformly to every implement
--    on the portal the way the original seed text implied.
-- 2. Confirmed live: SMAM funding is itself one of the schemes channeled
--    through this exact MP portal - it isn't a fully separate, parallel
--    program from the farmer's point of view. The state portal is the
--    delivery mechanism; SMAM (and RKVY, PMKSY for irrigation items) are
--    the underlying funding sources behind different implements on it.

UPDATE schemes
SET description = 'Madhya Pradesh''s farm-machinery subsidy portal, which delivers funding from several underlying central and state schemes (SMAM, RKVY, and others) depending on the implement - covers tractors, power tillers, combine harvesters, threshers, rotavators, and 40+ other implements. Applications go through a lottery-based allocation on the state''s DBT portal, in seasonal windows tied to whichever implements currently have an open application cycle.',
    benefit = 'Varies by implement and scheme route, confirmed live via the portal''s own Subsidy Calculator (13 Sep 2026): tractor attachments funded via RKVY General-DPR give 40% subsidy for general-category farmers and 50% for SC/ST farmers (e.g. a Ridger: Rs 12,000-16,000 max at 40% general vs Rs 15,000-20,000 max at 50% SC) - but some specific current-cycle implements (e.g. Happy Seeder, funded via SMAM under the RKVY cafeteria) give a flat 50% to every farmer category with no general/reserved split. Tractors themselves cap around Rs 1.5-2 lakh depending on category and HP class. Check the portal''s own Subsidy Calculator for the exact current rate for a specific implement rather than relying on one fixed percentage.',
    updated_at = NOW(6)
WHERE name = 'MP Krishi Yantra Anudan Yojana';
