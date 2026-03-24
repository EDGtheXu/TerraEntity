package org.confluence.terraentity.api.event;

import java.util.function.Supplier;

public interface IReDirectable<T> {

    void setRedirection(T reDirection);


    T getRedirectionOrDefault(Supplier<T> defaultValue);

}
