package io.github.ryu200o.eduworkshop.workshop.internal.application.exception;

import io.github.ryu200o.eduworkshop.shared.application.exception.ResourceNotFoundException;

public class WorkshopNotFoundException extends ResourceNotFoundException {

    public WorkshopNotFoundException(String field, Object value) {
        super("Workshop", field, value);
    }
}
