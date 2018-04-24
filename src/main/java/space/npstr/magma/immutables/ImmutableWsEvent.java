package space.npstr.magma.immutables;

import org.immutables.value.Value;

/**
 * Created by napster on 21.04.18.
 */
@Value.Style(
        typeImmutable = "*WsEvent",
        stagedBuilder = true
)
public @interface ImmutableWsEvent {
}
