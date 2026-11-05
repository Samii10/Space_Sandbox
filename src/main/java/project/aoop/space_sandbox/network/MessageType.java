package project.aoop.space_sandbox.network;

public enum MessageType {
    HELLO,     // guest -> host     HELLO|protocol
    WELCOME,   // host -> guest     WELCOME|protocol|level
    FULL,      // host -> guest     the room already has two players
    SHIP,      // both ways         SHIP|x|y|vx|vy|angle|health|thrusting   (20 per second)
    FIRE,      // both ways         FIRE|x|y|vx|vy|angle|lifetime           (one bullet)
    BEAM,      // both ways         BEAM|charge                             (laser fired)
    LEVEL,     // host -> guest     LEVEL|n
    PLANETS,   // host -> guest     PLANETS|Mercury=1.2345|Venus=...        (2 per second)
    PING,      // both ways         keep-alive
    BYE        // both ways         BYE|reason
}