# Bridge Diagnostics 0.8.2 — The Axiom

Purpose: diagnose persistent Wardrobe cosmetic sync postponed after 0.8.1. This is not a claimed fix for the live website.

Cosmetics POST reads the response and logs only the HTTP status and sanitized JSON error symbol, with 20-second throttling. Transport failures only log their class name. URLs, UUID, HMAC signatures, secrets, nonces, response bodies and tokens are never logged. HMAC signing and all skin routes are unchanged.

Validation: Java 21 signed HTTPS integration test for cosmetics POST/GET and skin POST/GET passed. HTTP 401/404/503 and network-error diagnostics passed. The server's actual response remains unknown.

Install the 0.8.2 JAR from the chat artifact after stopping Paper and keeping all original configs, restart, verify version AxiomWardrobe, select one cosmetic, and send only a Wardrobe cosmetics bridge diagnostic line. No need to modify AxiomBridge, HMCCosmetics, Unified or MySQL until the error is identified.
