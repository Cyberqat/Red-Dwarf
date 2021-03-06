package com.sun.sgs.example.swarm.shared.messages;

import java.io.Serializable;

import com.sun.sgs.example.swarm.shared.util.Direction;

/**
 *
 */
public class BuildMessage implements Message, Serializable
{
    /** The version of the serialized form of this class. */
    private static final long serialVersionUID = 1L;
    
    private Direction direction;
    
    /** Creates a new instance of BuildMessage */
    public BuildMessage(Direction direction)
    {
        this.direction = direction;
    }
    
    public Direction getDirection() { return direction; }
    
}
