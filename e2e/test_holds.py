"""
Hold lifecycle — the cross-service flow that breaks under manual testing.

Each test drives the Python backend's public API, which proxies to the Java
hold service, which (on confirm) calls back into the Python backend to create
the real booking. We assert the invariants that span that boundary.
"""

import os

import pytest
from helpers import (
    create_hold,
    create_quote,
    find_flight_with_seats,
    poll,
    seats_available,
    traveler_headers,
)


def test_confirm_creates_booking_and_decrements_seat(client, traveler):
    """Quote -> hold -> confirm: a real booking appears in Python and a seat drops."""
    flight_id = find_flight_with_seats(client, "business")
    before = seats_available(client, flight_id, "business")

    quote = create_quote(client, flight_id, traveler, "business", quantity=1)
    hold = create_hold(client, quote["quoteId"], traveler)
    assert hold["status"] == "HELD"
    # A hold alone reserves nothing in Python — seats only move on confirm.
    assert seats_available(client, flight_id, "business") == before

    r = client.post(f"/holds/{hold['holdId']}/confirm", headers=traveler_headers(traveler))
    r.raise_for_status()
    confirmed = r.json()
    assert confirmed["status"] == "CONFIRMED", confirmed
    booking_ref = confirmed.get("externalBookingReference")
    assert booking_ref, f"confirm should set a booking reference: {confirmed}"

    assert seats_available(client, flight_id, "business") == before - 1

    # The referenced booking really exists for our traveler.
    bookings = client.get(f"/bookings/{traveler['user_id']}").json()
    assert any(str(b["booking_id"]) == str(booking_ref) for b in bookings), \
        f"booking {booking_ref} not found in {bookings}"


def test_release_does_not_consume_seat(client, traveler):
    """Quote -> hold -> release: nothing is booked, availability is unchanged."""
    flight_id = find_flight_with_seats(client, "business")
    before = seats_available(client, flight_id, "business")

    quote = create_quote(client, flight_id, traveler, "business", quantity=1)
    hold = create_hold(client, quote["quoteId"], traveler)

    r = client.post(f"/holds/{hold['holdId']}/release", headers=traveler_headers(traveler))
    r.raise_for_status()
    assert r.json()["status"] == "RELEASED"

    assert seats_available(client, flight_id, "business") == before


def test_confirm_is_idempotent(client, traveler):
    """Confirming the same hold twice yields the same booking, not a double charge."""
    flight_id = find_flight_with_seats(client, "business")
    before = seats_available(client, flight_id, "business")

    quote = create_quote(client, flight_id, traveler, "business", quantity=1)
    hold = create_hold(client, quote["quoteId"], traveler)

    first = client.post(f"/holds/{hold['holdId']}/confirm", headers=traveler_headers(traveler)).json()
    after_first = seats_available(client, flight_id, "business")
    assert after_first == before - 1

    second = client.post(f"/holds/{hold['holdId']}/confirm", headers=traveler_headers(traveler)).json()
    assert second["externalBookingReference"] == first["externalBookingReference"]

    after_second = seats_available(client, flight_id, "business")
    assert after_second == after_first, "second confirm must not decrement again"


def test_hold_on_unknown_quote_returns_error(client, traveler):
    """Holding a non-existent quote surfaces an error rather than a fake hold.

    Documents a wart: the Python proxy swallows the Java 404 and returns
    HTTP 200 with an {"error": ...} body, so callers must inspect the body.
    """
    r = client.post("/quotes/Q-0000-000000/holds", headers=traveler_headers(traveler))
    body = r.json()
    assert "holdId" not in body
    assert "error" in body, f"expected an error body, got {body}"


def test_hold_auto_expiry(client, traveler):
    """With a short hold duration, an un-confirmed hold flips to EXPIRED and can't confirm."""
    if os.environ.get("E2E_RUN_SLOW") != "1":
        pytest.skip("set E2E_RUN_SLOW=1 to run (waits up to ~90s for the expiry scheduler)")

    flight_id = find_flight_with_seats(client, "business")
    quote = create_quote(client, flight_id, traveler, "business", quantity=1)
    hold = create_hold(client, quote["quoteId"], traveler)
    hold_id = hold["holdId"]

    final = poll(
        lambda: client.get(f"/holds/{hold_id}", headers=traveler_headers(traveler)).json(),
        until=lambda h: h.get("status") == "EXPIRED",
        timeout=120,
        interval=5,
    )
    assert final["status"] == "EXPIRED", f"hold never expired: {final}"

    # An expired hold cannot be confirmed into a booking.
    after_expiry = client.post(f"/holds/{hold_id}/confirm", headers=traveler_headers(traveler)).json()
    assert after_expiry.get("status") != "CONFIRMED", after_expiry


def test_other_traveler_cannot_access_hold(client, traveler):
    """A traveler who does not own a quote/hold cannot read, hold, release or confirm it.

    The Java service answers 404 (not 403) so IDs cannot be enumerated; the Python
    proxy turns that into HTTP 200 with an {"error": ...} body.
    """
    other = {"user_id": traveler["user_id"] + 100000}  # an ID that does not own the quote
    flight_id = find_flight_with_seats(client, "business")
    before = seats_available(client, flight_id, "business")

    quote = create_quote(client, flight_id, traveler, "business", quantity=1)
    hold = create_hold(client, quote["quoteId"], traveler)
    hold_id = hold["holdId"]

    # The other traveler sees nothing and can place no hold on the owner's quote.
    assert "error" in client.get(f"/quotes/{quote['quoteId']}", headers=traveler_headers(other)).json()
    assert "holdId" not in client.post(
        f"/quotes/{quote['quoteId']}/holds", headers=traveler_headers(other)
    ).json()
    assert "error" in client.get(f"/holds/{hold_id}", headers=traveler_headers(other)).json()
    assert "error" in client.post(f"/holds/{hold_id}/release", headers=traveler_headers(other)).json()
    assert "error" in client.post(f"/holds/{hold_id}/confirm", headers=traveler_headers(other)).json()

    # Nothing changed for the owner: the hold is still HELD and no seat was consumed.
    still = client.get(f"/holds/{hold_id}", headers=traveler_headers(traveler)).json()
    assert still["status"] == "HELD", still
    assert seats_available(client, flight_id, "business") == before

    # Clean up so the held seat does not linger.
    client.post(f"/holds/{hold_id}/release", headers=traveler_headers(traveler)).raise_for_status()


def test_missing_traveler_header_is_rejected(client, traveler):
    """The proxy requires X-Traveler-Id rather than forwarding an anonymous call."""
    flight_id = find_flight_with_seats(client, "business")
    quote = create_quote(client, flight_id, traveler, "business", quantity=1)

    assert client.get(f"/quotes/{quote['quoteId']}").status_code == 422
    assert client.post(f"/quotes/{quote['quoteId']}/holds").status_code == 422
