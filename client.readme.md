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
    byte hostLength;       // length of bindHost
    byte routeLength;      // length of route
    long ts;               // client System.nanoTime()
    short port;            // client port
    byte pathLength;       // length of path
    byte dependencyLength; // length of dependency
    byte[] bindHost;           // client IPv4 address/hostname as non-terminated UTF-8 string
    byte[] route;          // route (exported path, non-terminated UTF-8 string)
    byte[] path;           // path (internal path, non-terminated UTF-8 string)
    byte[] dependency;     // as single dependency to be subscribed (non-terminated UTF-8 string)
} 
```

The mappings are designed to be exchanged inside separate UDP packets.

## Configuration

Define the insect server using a property  
net.talpidae.net.insect.server=127.0.0.1:31234
