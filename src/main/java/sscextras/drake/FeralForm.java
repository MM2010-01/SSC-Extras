package sscextras.drake;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Identifier;
import net.onixary.shapeShifterCurseFabric.player_form.PlayerFormBase;

record FeralForm(EntityType<?> type, boolean drake, boolean familiar, boolean snowFox, Text name) {
    static FeralForm resolve(PlayerFormBase form) {
        String species = species(form.FormID.getPath());
        if (form.getGroup() == EarthenDrake.GROUP) {
            return new FeralForm(DrakeStable.DRAKE, true, false, false, DrakeStable.DRAKE.getName());
        }
        boolean familiar = species.equals("familiar_fox") || species.equals("witch_familiar");
        boolean snowFox = species.equals("snow_fox");
        EntityType<?> type = switch (species) {
            case "familiar_fox", "witch_familiar", "snow_fox" -> EntityType.FOX;
            case "anubis_wolf" -> EntityType.WOLF;
            case "feral_cat" -> EntityType.CAT;
            default -> matchingType(form.FormID.getNamespace(), species);
        };
        if (type == null && form.getGroup() != null) {
            String group = species(form.getGroup().GroupID.getPath());
            type = matchingType(form.getGroup().GroupID.getNamespace(), group);
        }
        return new FeralForm(type == null ? EntityType.ZOMBIE : type, false, familiar, snowFox,
                type == null || familiar || snowFox || species.equals("anubis_wolf") ? formName(form) : type.getName());
    }

    private static String species(String path) {
        return path.replaceFirst("^(?:form_|group_)", "").replaceFirst("_(?:[0-9]+|sp|form)$", "");
    }

    private static EntityType<?> matchingType(String namespace, String species) {
        return Registries.ENTITY_TYPE.getOrEmpty(new Identifier(namespace, species))
                .or(() -> Registries.ENTITY_TYPE.getOrEmpty(new Identifier("minecraft", species))).orElse(null);
    }

    private static Text formName(PlayerFormBase form) {
        var name = form.getFormName();
        if (!(name.getContent() instanceof TranslatableTextContent translation)) return name;
        var fallback = new StringBuilder();
        for (var word : species(form.FormID.getPath()).split("_")) {
            if (word.isEmpty()) continue;
            if (!fallback.isEmpty()) fallback.append(' ');
            fallback.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return Text.translatableWithFallback(translation.getKey(), fallback.toString(), translation.getArgs());
    }
}
