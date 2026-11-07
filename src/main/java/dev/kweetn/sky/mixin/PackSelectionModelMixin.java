package dev.kweetn.sky.mixin;

import net.minecraft.client.gui.screens.packs.PackSelectionModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.stream.Stream;

/** Hides the Kweetn Sky packs from the resource pack screen. */
@Mixin(PackSelectionModel.class)
public abstract class PackSelectionModelMixin {
    @Inject(method = "getSelected", at = @At("RETURN"), cancellable = true)
    private void kweetnsky$hideSelected(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(e -> !e.getId().startsWith("file/kweetn_sky_")));
    }

    @Inject(method = "getUnselected", at = @At("RETURN"), cancellable = true)
    private void kweetnsky$hideUnselected(CallbackInfoReturnable<Stream<PackSelectionModel.Entry>> cir) {
        cir.setReturnValue(cir.getReturnValue().filter(e -> !e.getId().startsWith("file/kweetn_sky_")));
    }
}
