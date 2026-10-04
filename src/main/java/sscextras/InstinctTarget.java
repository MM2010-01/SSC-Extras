package sscextras;

import net.minecraft.util.Identifier;

public interface InstinctTarget {
    double sscExtras$getBlockedGain();
    void sscExtras$addBlockedGain(double amount);
    Identifier sscExtras$getTarget();

    void sscExtras$setTarget(Identifier target);
}
