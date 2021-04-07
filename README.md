# Chord

This project is an implementation of [Chord](https://pdos.csail.mit.edu/papers/chord:sigcomm01/chord_sigcomm.pdf),
a protocol and algorithm for a peer-to-peer distributed hash table.

Note that this project is not meant to be used in production. It was not developed
with security and efficiency in mind and is instead meant to be a learning side project.

## Usage

```
lein run {port number}
```

This will start a Chord node at a chosen port. Run the same command with a different port
and/or on a different host to create another node. Repeat as you wish.

Once the nodes have been set up, fire up `lein repl` and use the client to join the nodes
into a Chord ring.

```
chord.core=> (require '[chord.client :as c])
nil
chord.core=> (c/join "{node-1-host}" {node-1-port} "{node-2-host}" {node-2-port})
{:join :ok}
```

Once all the nodes have joined a Chord ring, the client can then be used to query
any node to get information about the node that a hash (in this case, a sha-1 hash)
falls into.

```
chord.core=> (c/get "{node-host}" {node-port} (digest/sha-1 "hello world!"))
```

## Demo

A `docker-compose.yml` file is available to enable a quick local deployment of a Chord ring.
To start, using docker-compose:

```
docker-compose up --scale chord-peer=2 -d
```

This will create a ring containing three nodes, one being a leader and the other two peer nodes
joining the ring of the leader node.

Use the client by firing up a repl locally as described above. The nodes launched through
docker-compose can then be queried.

## To Do

- Handling of various failures. As in a real distributed system, network failures happen
and this project as it stands does not yet deal with those. Among those failures that are not yet
handled are the following:
  - Node failures. A Chord ring should be able to stabilize and recognize when a node fails.
  - Client errors. What to do when a client command fails. Retry?
- Test out the correctness of the implementation.
  - Are all hash lookups returning the right node?
  - Is the fix-fingers procedure working correctly?
  - etc...

## License

Copyright © 2021 Robert Narvaez

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
http://www.eclipse.org/legal/epl-2.0.