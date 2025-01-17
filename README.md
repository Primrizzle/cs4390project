# Outline for CS4390 Project

## Major Tasks
1. Sending data between the server/client
2. Receiving input from the user client-side and sending it to the server
3. Calculating the given equation and sending it to the proper user
4. Registering each user and properly sending the calculations to the proper user
5. Having a persistent logging method for each user
6. Map each UserID to some metadata
7. Maintaining a thread-pool to concurrently execute n-amount of calculations

## Server-Side Tasks
1. Keeping Track of All Users
    - We can use a HashMap of \<UserID, EnterTimestamp\>
    - UserID should probably be a string that must be generated on the client-side. This UserID can be randomly generated or provided by the user itself (will have to look into this more)
    - EnterTimestamp should probably be an integer representing the UNIX timestamp when the user accessed the server. We can calculate when the time the user was connected to the server by substracting the timestamp of the termination request to EnterTimestamp
2. Waiting for Client Request
    - We can make a listener thread and multiple other worker threads. These worker threads will handle the calculations and response back to the clients. While the listener thread will only focus on receiving responses and sending them to the worker threads
3. Simultaneous connections with multiple clients
    - Same logic as #2
4. Server should be able to perform math operations and return it to the proper sender
    - We can make it so that the message to the server includes sender information
    - As for calculating the math equations themselves, we can use a modified Shunting-Yard algorithm to both verify the equation but also calculate the value as well
5. Server should respond in a FIFO queue for the requests
6. Server should close a connection with a client once they are done and log this information
    - I think that we can simply use a text file to put all the logging information or print it to the console. I can email the TA/Prof if necessary

## Client-Side Tasks
1. Client gives a name and waits for acknowledgement
    - TCP connection and sending a message to the server with the client's name
2. Client can send basic math calculation requests
    - We'll have to go over the protocol of sending/receving and how answers/errors are handled
3. Multiple clients can join and send requests
    - We'll have multiple worker threads that'll handle the client requests
4. A client should send a close connection request to the server once it terminates

## Message Protocol
I would like to use JSON, but I think we need to import a library to handle that or look into if Java has a serialization functionality we can use to send data. Serialization is basically turning the data into a "standard" form of bytes for another program to read (of course there has to be a standard for these bytes)

We can keep logs of a client in a text file like
```
User 1: Logged in at 9:37 AM on January 1, 2024
User 1: Calculated "2 + 2"
User 2: Logged in at 9:38 AM on January 1, 2024
User 1: Logged out at 9:40 AM on January 1, 2024
...
```

### Java Serialization Message Formats
These are if we stick to using the built-in serialization functions provided by default.

#### Sending/Receiving Math Calculations
```Java
public class Calculate {
    // We can just use the name that the client provided to the server
    public String sender;
    /*
     * Message can be the equation, the value of the equation, or the error message
     * 
     * We don't need any other boolean values for the message as if the client receives something,
     * then the client will most likely just print it to the screen regardless
     */
    public String message;
}
```
This goes for both sending to the server and receiving from the server

#### Joining a Connection
```Java
public class ConnectionAttempt {
    public String name;
    public boolean accepted;
}
```
The client send a `ConnectionAttempt` with the name and the server will verify if the name is available. If it is, then it'll register the name with the associated client and modify the `accepted` attribute to be `true`. Otherwise, it'll be `false`. In both scenarios, `ConnectionAttempt` will be returned back to the sender.

#### Terminating a Connection
```Java
public class TerminateConnection {
    public String name;
}
```

#### Format of keeping logs server side
We could use SQLite to store the logs if you're ok with that. I don't know if the professor wants us to keep this stored on the disk or in memory
