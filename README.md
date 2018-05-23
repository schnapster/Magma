# Magma

[![Release](https://img.shields.io/github/tag/napstr/Magma.svg)](https://jitpack.io/#space.npstr/Magma)
[![Build Status Master Branch](https://travis-ci.com/napstr/Magma.svg?branch=master)](https://travis-ci.org/napstr/Magma/branches)
[![License](https://img.shields.io/github/license/napstr/Magma.svg)]()

A voice only API for Discord, focused on delivering music at scale.

![Lava? Magma?](https://i.imgur.com/8Nudc2k.png)


Notable features:
- Event based and non-blocking at its core

Not supported:
- Audio Receiving


Magma is a heavily modified fork of [JDA-Audio](https://github.com/DV8FromTheWorld/JDA-Audio) (Apache 2.0)

Big shout-out to the to the original authors and maintainers for doing great work!

Besides making use of most of the code for handling the Opus library and audio packets,
Magma reuses some of JDAs APIs, namely:
- IAudioSendSystem
- IAudioSendFactory
- IPacketProvider
- AudioSendHandler

Magma does not ship any implementations for IAudioSendSystem and IAudioSendFactory.
It is important that the implementation you choose will never call any method of the packet provider but
`IPacketProvider#getNextPacket`, because none of the others are supported by Magma.
Recommended implementations:
- https://github.com/sedmelluq/jda-nas
- https://github.com/Shredder121/jda-async-packetprovider


## Get started

### Add Magma to your project

- [JitPack](https://jitpack.io/#space.npstr/Magma) for builds straight from github code

###### Gradle build.gradle
```groovy
    repositories {
        maven { url 'https://jitpack.io' }
    }

    dependencies {
        compile group: 'space.npstr', name: 'Magma', version: 'x.y.z'
    }
```

###### Maven pom.xml
```xml
    <repositories>
        <repository>
            <id>jitpack.io</id>
            <url>https://jitpack.io</url>
        </repository>        
    </repositories>

    <dependency>
        <groupId>space.npstr</groupId>
        <artifactId>Magma</artifactId>
        <version>x.z.y</version>
    </dependency>
```

### Sample code

Discord supports one single audio connection per user and guild (also called a "member").
This means, an audio connection is exactly identified by those two datapoints,
and all of the methods of Magma require those to correctly identify the connection
that you want to open/close/change something about.


#### DSL

Magma uses [immutables.org](http://immutables.org/) to ensure type and parameter safety,
both internally and in the Api you are going to use.
Concretely, the Api makes use of immutable `Member` and `ServerUpdate` objects.

```java

    Member member = MagmaMember.builder()
        .userId("...")
        .guildId("...")
        .build();
    
    ServerUpdate = MagmaServerUpdate.builder()
        .sessionId("...")
        .endpoint("...")
        .token("...")
        .build();
    
```

#### Api

Typical usage of the methods offered by the Api:

```java

    IAudioSendFactory audioSendFactory = <your implementation here>;
    AudioSendHandler sendHandler = <your implementation here>;

    MagmaApi magmaApi = MagmaApi.of(__ -> audioSendFactory);
    magmaApi.provideVoiceServerUpdate(member, serverUpdate);
    magmaApi.setSendHandler(member, sendHandler);


    // music plays, then later:

    magmaApi.setSendHandler(member, someOtherHandler);

    // other handler plays music / sounds


    // to clean up:

    magmaApi.removeSendHandler(member);
    magmaApi.closeConnection(member);

    // on shutting down

    magmaApi.shutdown();

    // Calling any other methods of a MagmaApi object after having called shutdown() 
    // will result in undefined behaviour. Do not do this, create a new MagmaApi instead.
    // Please note that you are strongly encouraged to use a single MagmaApi object throughout 
    // the lifecycle of your application.

```

None of those calls are blocking, as they are translated into events to be processed as soon as possible.
Currently, there is no feedback as to when and how these are processed.


## Numbers
_(last updated for 0.2.1)_

Magma has been written with [Lavalink](https://github.com/Frederikam/Lavalink) in mind,
numbers shown here compare vanilla Lavalink to a [Magma-based branch](https://github.com/Frederikam/Lavalink/tree/experimental/magma).

Graphs by courtesy of [FredBoat](https://github.com/Frederikam/FredBoat/).

#### CPU Usage
<details><summary>Click me</summary>

![CPU Usage](https://i.imgur.com/X7cvuzO.png)

</details>

#### Threads
<details><summary>Click me</summary>

![Threads](https://i.imgur.com/rnYgGtw.png)

</details>

#### Garbage Collection
<details><summary>Click me</summary>

![GC Time Spent](https://i.imgur.com/X7yC5xh.png)  
![GC Runs](https://i.imgur.com/pLKH2hA.png)

</details>

#### Memory
<details><summary>Click me</summary>

![JVM Memory](https://i.imgur.com/21IdlIo.png)  
![Process Memory](https://i.imgur.com/Pu8jwec.png)

</details>

#### Audio Frames
<details><summary>Click me</summary>

![Audio Frames Lost](https://i.imgur.com/wmBlup7.png)

</details>


## Changelog

Expect breaking changes between versions while v1 has not been released.

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


## Dependencies:

- **Opus Java**:
  - [Source Code](https://github.com/discord-java/opus-java)
  - [Apache 2.0](https://github.com/discord-java/opus-java/blob/634c74a76c311252a6b5c91b1533d2baa7990406/src/main/java/net/dv8tion/jda/core/utils/NativeUtil.java)

- **JSON In Java**:
  - [Website](http://json.org/)
  - [Source Code](https://github.com/stleary/JSON-java)
  - [The JSON License](http://json.org/license)
  - [Maven Repository](https://mvnrepository.com/artifact/org.json/json)

- **Simple Logging Facade for Java**:
  - [Website](https://www.slf4j.org/)
  - [Source Code](https://github.com/qos-ch/slf4j)
  - [MIT License](http://www.opensource.org/licenses/mit-license.php)
  - [Maven Repository](https://mvnrepository.com/artifact/org.slf4j/slf4j-api/)

- **Spring Webflux**:
  - [Website](https://projects.spring.io/spring-framework/)
  - [Source Code](https://github.com/spring-projects/spring-framework)
  - [Apache 2.0](http://www.apache.org/licenses/LICENSE-2.0)
  - [Maven Repository](https://mvnrepository.com/artifact/org.springframework/spring-webflux)
  
- **Undertow Core**:
  - [Website](http://undertow.io/)
  - [Source Code](https://github.com/undertow-io/undertow)
  - [Apache License Version 2.0](http://repository.jboss.org/licenses/apache-2.0.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/io.undertow/undertow-core) 

- **SpotBugs Annotations**:
  - [Website](https://spotbugs.github.io/)
  - [Source Code](https://github.com/spotbugs/spotbugs)
  - [GNU LESSER GENERAL PUBLIC LICENSE, Version 2.1](https://www.gnu.org/licenses/old-licenses/lgpl-2.1.en.html)
  - [Maven Repository](https://mvnrepository.com/artifact/com.github.spotbugs/spotbugs-annotations)

- **Immutables.org Value**:
  - [Website](http://immutables.org/)
  - [Source Code](https://github.com/immutables/immutables)
  - [The Apache Software License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0.txt)
  - [Maven Repository](https://mvnrepository.com/artifact/org.immutables/value)
