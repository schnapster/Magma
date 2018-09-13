package space.npstr.magma.immutables;

import org.immutables.value.Value;

@Value.Style(
        typeImmutable = "*ApiEvent",
        stagedBuilder = true
)
public @interface ImmutableApiEvent {}
