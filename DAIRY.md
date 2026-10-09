# Dairy: products, delivery areas and slots, subscriptions

What it is, how to switch it on, and what it does. Nothing here contains real data: products, prices, pincodes and
timings are entered by the owner in the admin screens.

## Switches (environment variables, all optional)

| Variable | Default | Effect |
| --- | --- | --- |
| `DAIRY_SUBSCRIPTIONS_ENABLED` | `false` | Customers can create subscriptions; the web shows the subscribe/manage screens. Off: creating one returns a clear "not available yet" (422) and the web hides the screens. |
| `DAIRY_SUBSCRIPTIONS_JOB_ENABLED` | `false` | The nightly job prepares next-day deliveries. Off: the job does nothing at all (no delivery rows, no stock change, no notification, no email). |
| `DAIRY_SUBSCRIPTIONS_MAX_QUANTITY` | `20` | Largest pack count per delivery. |
| `DAIRY_DELIVERY_MIN_LEAD_DAYS` | `1` | Earliest delivery for a one-time dairy order, in days from today (1 = tomorrow). |
| `DAIRY_DELIVERY_WINDOW_DAYS` | `7` | How many days from the earliest date a customer can pick. |
| `dairy.subscriptions.job-cron` (property) | `0 0 20,22 * * *` | When the job runs (IST). Two runs so one missed while the server restarted is made up; preparing a date twice is harmless. |

None is required: the server boots with none set. Turn subscriptions on in this order: enable the customer switch and the
job switch together, once products, delivery areas and slots exist.

## Data model (migration V42, additive)

* `product_dairy_details`: unit, pack size, shelf life, fresh-daily, storage note; one row per dairy product.
* A product is *dairy* when its category is the `dairy` category or a descendant. The `Dairy` category row is created by the
  migration; it is hidden from the public category list until it has an active product.
* `delivery_areas` (pincodes) and `delivery_slots` (name, window, weekdays), managed in admin. **No rows means no
  restriction / nothing to choose.**
* `order_delivery`: the slot and date chosen at checkout, only for orders that contain dairy.
* `dairy_subscriptions`, `dairy_subscription_skips`, `dairy_deliveries` (unique per subscription and date).

## Behaviour

* **Checkout**: pincode and slot rules apply only when the cart contains a dairy product. Any other cart is unchanged.
  `deliverySlotId` and `deliveryDate` are optional fields on the checkout request; older clients that send only
  `addressId` keep working until slots are configured.
* **Subscriptions pay at the door** (cash or UPI on delivery). No Razorpay, no order or payment rows.
* **Nightly job**: for tomorrow (IST) it creates one delivery per due subscription. Stock is taken under the product row
  lock (the lock checkout uses), so a unit can only go to one buyer. Not enough stock: the delivery is recorded as
  `SKIPPED_OUT_OF_STOCK` and the customer is notified. A customer skip, pause or cancel for a prepared date gives the stock back.
  A delivery marked failed does not restock (the milk went out).
* **Deletion of an account** is blocked while a dairy delivery is scheduled; erasing an account cancels its subscriptions and
  scrubs the saved addresses (`AccountErasureRepository` and `backup/reapply-deletions.sql` are kept identical by a test).

## API

* Public (GET): `/api/v1/dairy-store/config`, `/products`, `/product-ids`, `/delivery-options?pincode=`
* Customer: `/api/v1/dairy/subscriptions` (create, list, get, deliveries, pause, resume, skip, unskip, cancel). Another
  customer's subscription is a 404.
* Admin: `/api/v1/admin/dairy/delivery-areas`, `/delivery-slots`, `/subscriptions`, `/deliveries`, `/deliveries/export` (CSV),
  `/deliveries/{id}/delivered`, `/deliveries/{id}/failed`. Admin mutations are written to the audit log.

## Rolling back

The migration only adds tables and one category row; nothing existing is altered, so the previous server version runs fine
on the migrated database. Switching the two flags off stops all dairy automation immediately.
