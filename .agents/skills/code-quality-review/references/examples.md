# Code Quality Review Examples

Use these examples to interpret nuanced rules. Adapt them to the language and repository conventions; do not copy them mechanically.

## U1 — Exhaustive Branches

Prefer compiler-enforced exhaustiveness for a closed type:

```java
String label = switch (state) {
    case READY -> "ready";
    case RUNNING -> "running";
    case FAILED -> "failed";
};
```

At an untrusted integer or protocol boundary, enumerate known values and reject the genuine remainder:

```java
return switch (statusCode) {
    case STATUS_READY -> Status.READY;
    case STATUS_RUNNING -> Status.RUNNING;
    case STATUS_FAILED -> Status.FAILED;
    default -> throw new IllegalArgumentException("Unknown status code: " + statusCode);
};
```

Do not use `default` for `Status.FAILED` merely because it is the only known case left; doing so can silently reinterpret a newly introduced value.

## R4 — Magic Literals

`retryCount == 0` is normally self-explanatory. A domain threshold benefits from a name:

```java
private static final int MAX_AUTHENTICATION_ATTEMPTS = 5;

if (failedAttempts >= MAX_AUTHENTICATION_ATTEMPTS) {
    lockAccount();
}
```

The name should explain the business meaning, not merely rename the token as `Five`.

## R12 — Guard Clauses

Make exceptional cases exit before the main operation:

```java
if (!request.isValid()) {
    return Result.invalidRequest();
}
if (!user.canSubmit(request)) {
    return Result.forbidden();
}

return submit(request);
```

Do not introduce early returns when they would make cleanup or state transitions less clear. In Java, prefer `try`-with-resources or `finally` for cleanup instead of relying on a fragile single exit path.

## R11 — Single Level of Abstraction

A high-level workflow should read at one conceptual level:

```text
validateOrder(order)
reserveInventory(order)
chargeCustomer(order)
scheduleShipment(order)
```

Parsing individual bytes or assembling SQL in the middle of that workflow mixes low-level mechanics with business operations and should usually move behind a focused helper.

## C1–C3 — Comments

Avoid repetition:

```java
// Increment retry count.
++retryCount;
```

Explain a non-obvious constraint:

```java
// The provider may deliver the same event more than once, so persistence must be idempotent.
saveEventIfNew(event);
```

Mechanics are appropriate when the algorithm cannot be made obvious through structure and names alone, but keep the explanation focused on what a maintainer needs to preserve.

## U6 — Duplication

Extract repeated code when both occurrences implement the same policy and must evolve together. Leave small coincidental similarities separate when they belong to different domains or are likely to evolve independently. If that distinction is not obvious, state it in the review rather than forcing an abstraction.

## R3 and R5 — Expressions and Implicit Behavior

Replace a precedence-dependent compound condition:

```java
boolean isPrivileged = user.isAdmin() || user.isOwner();
boolean mayPublish = isPrivileged && !document.isLocked();
if (mayPublish) {
    publish(document);
}
```

Use an explicit conversion when Java numeric promotion, narrowing, boxing or unboxing, string concatenation, or overload selection could obscure meaning or produce an unintended result. Do not add casts or parentheses that merely restate universally clear idioms.
