-- V41__usage_daily.sql
-- Item 5 (usage analytics), Stage 4: anonymous daily counters of how often each public feature is used.
--
-- One row per (day, feature) holding a running total. There is deliberately NO user id, IP address, device,
-- cookie, user agent, request address or free text here: the table cannot say who did anything, only how
-- many times a feature was used on a day.
--  * day     = the calendar day in India time (Asia/Kolkata), written by the application
--  * feature = a fixed key such as 'product_detail' or 'mandi_prices' (see UsageFeature in the server code)
--  * count   = requests counted that day
-- Purely additive: a new table that nothing existing reads or writes. Older server code and older mobile
-- apps never see it.

CREATE TABLE usage_daily (
    day DATE NOT NULL,
    feature VARCHAR(40) NOT NULL,
    `count` BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (day, feature)
);
