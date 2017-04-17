# insect - simple, light-weight service registry client

## Inner Workings

A central server is periodically updated with important configuration parameters.

Clients can subscribe the server for information about specific or all registered route to service mappings.

An endpoint to service mapping looks like this:
```
public class Mapping
{
    // all numeric fields are big-endian
    byte type;             // message type (1: mapping)
    byte flags;            // 0x0
    byte hostLength;       // length of bindAddress
    byte routeLength;      // length of route
    long ts;               // client System.nanoTime()
    short port;            // client port
    byte nameLength;       // length of unique service name
    byte dependencyLength; // length of dependency
    byte[] host;           // client IPv4 address/hostname as non-terminated UTF-8 string
    byte[] route;          // route (exported path, non-terminated UTF-8 string)
    byte[] name;           // unique service name (non-terminated UTF-8 string)
    byte[] dependency;     // as single dependency to be subscribed (non-terminated UTF-8 string)
} 
```

A queen can request slave shutdown via this message: 
```
public class Shutdown
{
    byte type;             // message type (2: shutdown)
    byte magic;            // 0x86
} 
```

All messages are designed to be exchanged inside separate UDP packets.

## Configuration

The slave supports several command line options if default settings are used:
```
--insect.slave.route ROUTE_NAME      Set this slave's service name to ROUTE_NAME.
--insect.slave.remote REMOTE         Add a remote queen to register with (can be specified multiple times).
--insect.slave.timeout TIMEOUT_MS    Time in milliseconds until a dead remote slave is purged from the local cache.
```
