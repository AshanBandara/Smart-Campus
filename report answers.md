# Chapter 1 — Service Architecture & Setup

## 1.1 — Project & Application Configuration
**Question:** **In your report, explain the default lifecycle of a JAX-RS Resource class. Is a new instance instantiated for every incoming request, or does the runtime treat it as a singleton? Elaborate on how this architectural decision impacts the way you manage and synchronize your in-memory data structures (maps/lists) to prevent data loss or race conditions.** 

By default, a JAX-RS resource class is created per request, not as a singleton. That means the framework constructs a fresh instance for each incoming HTTP request, handles the request with that instance, and then discards it. As a result, any data stored in normal instance fields will not survive beyond that single request.

This matters a lot for in-memory storage. For example, if a `SensorResource` class stored sensors in a private field such as `private Map<String, Sensor> sensors = new HashMap<>()`, that map would be recreated every time, so previously stored data would appear to vanish. To avoid this, shared application data is kept in `DataStore.java` using `static final` fields, which remain available for the life of the JVM.

`ConcurrentHashMap` is used instead of `HashMap` because the server can process multiple requests at the same time. A plain `HashMap` is not thread-safe, so concurrent reads and writes could cause race conditions or data corruption. `ConcurrentHashMap` is designed for safe use in a multi-threaded environment, which makes it the appropriate choice here.

## 1.2 — The Discovery Endpoint
**Question:** **Why is the provision of hypermedia (links and navigation within responses) considered a hallmark of advanced RESTful design (HATEOAS)? How does this approach benefit client developers compared to static documentation?**

HATEOAS is considered an important part of advanced REST design because it makes the API easier to navigate and more self-descriptive. Rather than returning only data, the discovery endpoint includes links to related resources and available actions, such as `/api/v1/rooms` and `/api/v1/sensors`. This allows clients to discover the API structure at runtime instead of relying entirely on hardcoded paths.

Compared with static documentation, this is much more flexible. With static docs, developers must read external material and manually update their code when endpoint paths change. With HATEOAS, clients can follow links returned by the server, similar to how a browser follows hyperlinks. This reduces coupling between client and server and makes the API easier to maintain over time.

# Chapter 2 — Room Management

## 2.1 — Room Resource Implementation
**Question:** **When returning a list of rooms, what are the implications of returning only IDs versus returning the full room objects? Consider network bandwidth and client side processing.**

Returning only room IDs keeps the response small, which is useful when the client only needs to know what room identifiers exist. However, this usually pushes more work onto the client, because it must make additional requests for each room to fetch full details. For a large number of rooms, that can lead to the N+1 request problem and noticeably increase latency.

Returning complete room objects uses more bandwidth, but it gives the client everything it needs in a single response. That is usually better for a campus management interface, where the UI likely needs room names, capacities, and related information together. A sensible compromise is to return a lightweight summary, such as room ID, name, and capacity, while leaving more detailed fields for a separate request when needed.

## 2.2 — Room Deletion & Safety Logic
**Question:** **Is the DELETE operation idempotent in your implementation? Provide a detailed justification by describing what happens if a client mistakenly sends the exact same DELETE request for a room multiple times.**

Yes, the `DELETE` operation is idempotent in this implementation. In practice, that means repeating the same `DELETE` request does not change the final state beyond the first successful deletion. If the room exists and is empty, the first request removes it and returns `204 No Content`. A later request for the same room finds nothing and returns `404 Not Found`, but the room still remains deleted.

The response code changes, but the system state does not. That is what makes the operation idempotent. By contrast, repeating a `POST` request would usually create multiple resources, so it would not be idempotent.

# Chapter 3 — Sensor Operations & Linking

## 3.1 — Sensor Resource & Integrity
**Question:** **We explicitly use the `@Consumes(MediaType.APPLICATION_JSON)` annotation on the POST method. Explain the technical consequences if a client attempts to send data in a different format, such as `text/plain` or `application/xml`. How does JAX-RS handle this mismatch?**

If a method is annotated with `@Consumes(MediaType.APPLICATION_JSON)`, JAX-RS checks the incoming `Content-Type` header before calling the method. If the client sends something like `text/plain` or `application/xml`, the request does not match the method’s expected media type. In that case, the runtime returns `415 Unsupported Media Type` automatically.

This is useful because it prevents invalid requests from reaching the method body at all. The developer does not need to write separate content-type validation code. It also makes the API safer and more predictable, since each resource method only processes data in the format it expects.

## 3.2 — Filtered Retrieval & Search
**Question:** **You implemented this filtering using `@QueryParam`. Contrast this with an alternative design where the type is part of the URL path (e.g., `/api/v1/sensors/type/CO2`). Why is the query parameter approach generally considered superior for filtering and searching collections?**

Using `@QueryParam` is the better design for filtering a collection. For example, `/sensors?type=CO2` clearly means “return sensors matching this type,” which is exactly what a filter should do. Query parameters are meant for optional search criteria, sorting, and filtering, so they fit REST conventions well.

A path-based design such as `/api/v1/sensors/type/CO2` is less flexible. It makes `type` look like a resource name rather than a filter, which can cause confusion about what the endpoint actually represents. It also becomes awkward when multiple filters are needed, while query parameters can be combined naturally, such as `?type=CO2&status=ACTIVE`.

# Chapter 4 — Deep Nesting with Sub-Resources

## 4.1 — The Sub-Resource Locator Pattern
**Question:** **Discuss the architectural benefits of the Sub-Resource Locator pattern. How does delegating logic to separate classes help manage complexity in large APIs compared to defining every nested path (e.g., `sensors/{id}/readings/{rid}`) in one massive controller class?**

The sub-resource locator pattern is useful because it lets one resource delegate nested paths to another class instead of handling everything in a single class. In this API, `SensorResource` can manage general sensor operations and then return a `SensorReadingResource` for paths like `/{sensorId}/readings`. Jersey then continues routing requests to that returned class.

This improves the design in several ways. Each class has a narrower responsibility, so the code is easier to read and maintain. It also makes testing simpler, because nested behaviour can be tested separately. In larger APIs, this pattern helps prevent resource classes from becoming too large and difficult to manage.

# Chapter 5 — Advanced Error Handling & Logging

## 5.2 — Dependency Validation (422 Unprocessable Entity)
**Question:** **Why is HTTP 422 often considered more semantically accurate than a standard 404 when the issue is a missing reference inside a valid JSON payload?**

HTTP `422 Unprocessable Entity` is more accurate than `404 Not Found` when the URL is valid but the data inside the request is semantically incorrect. In this case, the endpoint exists and the JSON is properly formed, but a field such as `roomId` refers to a room that does not exist. The problem is not the URL; the problem is the meaning of the request body.

Using `404` would suggest that the endpoint itself is missing, which would mislead the client. `422` tells the client that the request was understood and parsed correctly, but the data failed business validation. That distinction makes it easier to identify and fix the real issue.

## 5.4 — The Global Safety Net (500)
**Question:** **From a cybersecurity standpoint, explain the risks associated with exposing internal Java stack traces to external API consumers. What specific information could an attacker gather from such a trace?**

Exposing raw Java stack traces to API consumers is risky because it leaks internal implementation details. Stack traces can reveal package names, framework versions, class names, file paths, and line numbers. An attacker could use that information to learn how the system is built and potentially identify known vulnerabilities or weak points.

The safer approach is to log the full exception on the server and return only a generic error response to the client. That is what the global exception mapper does: it keeps detailed debugging information available to administrators while avoiding unnecessary exposure to external users. This improves security without reducing the usefulness of server-side logs.

## 5.5 — API Request & Response Logging Filters
**Question:** **Why is it advantageous to use JAX-RS filters for cross-cutting concerns like logging, rather than manually inserting `Logger.info()` statements inside every single resource method?**

Using JAX-RS filters for logging is better than adding `Logger.info()` calls inside every resource method because logging is a cross-cutting concern. If it is written directly into each endpoint, the code becomes repetitive and harder to maintain. It also increases the risk that new endpoints will be added without proper logging.

A single `@Provider` logging filter can apply the same request and response logging logic across the entire API. That keeps resource classes focused on their main job and avoids mixing business logic with infrastructure code. It also makes future changes easier, because the logging behaviour can be updated in one place instead of many.
