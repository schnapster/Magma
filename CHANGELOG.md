## Changelog

Expect breaking changes between minor versions while v1 has not been released.

### v0.12.4
- Switch to "modern" echo message format [#25](https://github.com/napstr/Magma/pull/25)
- Adhere to Discord's user agent format [#26](https://github.com/napstr/Magma/pull/26)
- Build on jitpack with java8 [#27](https://github.com/napstr/Magma/pull/27)

### v0.12.3
- Send a basic user agent header when connecting to Discord

### v0.12.2
- Fixed jitpack versioning troubles. Previous versions might not resolve correctly, use this one instead.

### v0.12.0
- Align versions with Spring Boot's dependency management (may result in versions of transitive dependencies going up / down)
- Internal: Dependency locking & version ranges

### v0.11.0

- Picked up the commits from [Minn's Magma fork](https://github.com/MinnDevelopment/Magma):

    - 0.10.4: Fix xsalsa20_poly1305 encryption (legacy support)
    - 0.10.3: Convert to single DatagramSocket shared by all audio pipelines
    - 0.10.2: Fix possible memory leak due to unclosed datagram sockets
    - 0.10.1: Fix incorrect handling of ByteBuffer in asDatagramPacket
    - 0.10.0: Restructure for JDA V4 compatibility
    - 0.9.2: Fix handling of 4014 close code
    - 0.9.1:
        - Add speaking modes
        - Change transitive dependencies to proper api/implementation scopes
        - Fix several javadoc errors
    - 0.9.0: Support Java 8 through 10


### ~~v0.10.0~~

This version has been skipped to avoid confusion with versions from [Minn's Magma fork](https://github.com/MinnDevelopment/Magma).  
Minn's improvements and updates have been incorporated in the next minor version.


### v0.9.0
- Dependencies bumped
- **Breaking**: Reorganized the project into `api` and `impl` modules, resulting in new Maven/Gradle coordinates and new packages of the Magma classes
<details><summary>Click me</summary>

    `space.npstr.magma.MagmaApi` -> `space.npstr.magma.api.MagmaApi`  
    `space.npstr.magma.MdcKey` -> `space.npstr.magma.api.MdcKey`  
    `space.npstr.magma.Member` -> `space.npstr.magma.api.Member`  
    `space.npstr.magma.MagmaMember` -> `space.npstr.magma.api.MagmaMember`  
    `space.npstr.magma.ServerUpdate` -> `space.npstr.magma.api.ServerUpdate`  
    `space.npstr.magma.MagmaServerUpdate` -> `space.npstr.magma.api.MagmaServerUpdate`  
    `space.npstr.magma.WebsocketConnectionState` -> `space.npstr.magma.api.WebsocketConnectionState`  
    `space.npstr.magma.MagmaWebsocketConnectionState` -> `space.npstr.magma.api.MagmaWebsocketConnectionState`  
    `space.npstr.magma.events.api.MagmaEvent` -> `space.npstr.magma.api.event.MagmaEvent`  
    `space.npstr.magma.events.api.WebSocketClosed` -> `space.npstr.magma.api.event.WebSocketClosed`  
    `space.npstr.magma.events.api.WebSocketClosedApiEvent` -> `space.npstr.magma.api.event.WebSocketClosedApiEvent`  
    `MagmaApi.of` -> `MagmaFactory.of`

  </details>

### v0.8.3
- Fix bug with reconnecting in the same guild introduced in 0.8.2

### v0.8.2
- Idempotent handling of connection requests [\#16](https://github.com/napstr/Magma/pull/16) (thanks @Frederikam)

### v0.8.1
- Fix jitpack build

### v0.8.0
- Add a WebsocketConnectionState to report the state of the websocket connections managed by a MagmaApi

### v0.7.0
- Bump dependencies, including Java 11.

### v0.6.0
- Introduce `MagmaApi#getEventStream` that allows user code to consume events from Magma, for example when the
websocket is closed.

### v0.5.0
- Port the remaining changes of JDA 3.7 (see [\#651](https://github.com/DV8FromTheWorld/JDA/pull/651)),
notably the switch from `byte[]`s to `ByteBuffer`s in most places. This includes a backwards incompatible change to
`IPacketProvider`, marking it as a non-threadsafe class.  
**Known issues:** Applications using [japp](https://github.com/Shredder121/jda-async-packetprovider)
1.2 or below have broken audio output.

### v0.4.5
- Use direct byte buffers (off heap) for Undertow

### v0.4.4
- Send our SSRC along with OP 5 Speaking updates
- Add MDC and more trace logs to enable better reporting of issues
- Update close code handling for expected 1xxx codes and warnings on suspicious closes
- Deal with Opus interpolation

### v0.4.3
- Fix 4003s closes due to sending events before identifying

### v0.4.0
- Opus conversion removed. Send handlers are expected to provide opus packets.
- Fix for a possible leak of send handlers

### v0.3.3
- Fully event based AudioConnection
- Correct Schedulers used for event processing
- Dependency updates
- Code quality improvements via SonarCloud

### v0.3.2
- Log endpoint to which the connection has been closed along with the reason
- Share a BufferPool between all connections to avoid memory leak

### v0.3.1
- Dependency updates
- Additional experimental build against Java 11

### v0.3.0
- Type and parameter safety in the Api by introducing a simple DSL

### v0.2.1
- Handle op 14 events

### v0.2.0
- Build with java 10

### v0.1.2
- Implement v4 of Discords Voice API

### v0.1.1
- Depend on opus-java through jitpack instead of a git submodule

### v0.1.0
- Ignore more irrelevant events
- Smol docs update
- Licensed as Apache 2.0
- Use IP provided by Discord instead of endpoint address for the UDP connection

### v0.0.1
- It's working, including resumes.
