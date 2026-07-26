package io.github.ryu200o.eduworkshop.workshop.internal.domain.model.exception;

public class WorkshopCapacityExceedsRoomException extends RuntimeException {

    private final int workshopCapacity;
    private final int roomCapacity;

    public WorkshopCapacityExceedsRoomException(int workshopCapacity, int roomCapacity) {
        super("Workshop capacity (" + workshopCapacity + ") exceeds room capacity (" + roomCapacity + ")");
        this.workshopCapacity = workshopCapacity;
        this.roomCapacity = roomCapacity;
    }

    public int workshopCapacity() { return workshopCapacity; }

    public int roomCapacity() { return roomCapacity; }
}
