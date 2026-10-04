## Expanding the Original SSC Transformation Contents.

SSC Extras is an addon for Shape Shifter Curse that expands transformation progression, introduces feralizing collars, and lets transformed players use restricted equipment through the Feral Effigy.

## Instinct-Based Progression

The curse may build before your first transformation. Your instinct gauge is now visible in Original Shifter form, and cursed-creature attacks build instinct toward that creature’s form.
(Changed mod style)

Curse potions add instincts too. Even after transformed, matching creatures and potions continue continue to build your instinct gauge.

Permanent transformation is no longer locked and you may progress through instinct gains. 

### Cooling Down Your instincts
Now You may cool down your instincts while in the original form and the stage 2 form (before permanent) by not indulging in your instincts.

### Cursed Moonlight

Cursed Moons no longer freeze your instinct gauge. Exposed to it will increase your instinct, hide under a roof! (Or bask in the moonlight, your choice!)

### More Cursed Encounters

Watchout for modded cursed creatures (and the witch). They will continue to spread the curse until your one of them!
SSC Addon creatures compatible.

## Feralizing Collars

Accelerate your transformation with two wearable collars.

### Feralizing Collar

Crafted from leather surrounded by eight Untreated Moondust, this collar grants +1 instinct per second and doubles positive instinct gains. It can be removed freely.

### Cursed Feralizing Collar

Found in structure loot chests, this purple collar grants +2 instinct per second and quadruples positive instinct gains.

It can bind itself to cursed players when their necklace slot is empty, so watch out while looting if youre transformed. 

Wearing one while uncursed (if you really want to) starts a random form curse, unless the collar has been infused with a specific one.

A bound collar can be removed after reaching permanent form, or with a Cleansing Key. Inhibitors no longer remove collars.

### Choose your curse

Craft either collar with a form curse potion to infuse it. An infused collar starts that curse when worn by an uncursed player.

### Cleansing Key

Use this key to unequip worn Cursed Feralizing Collars, Cursed Reins and Cursed Saddles, dropping them nearby. It has six uses and spends one per removed item, stopping when it runs out. Your current form and instinct stay unchanged.

Removing an item also grants **Curse Cleansed** for 30 seconds, preventing cursed items from equipping themselves from your inventory or containers. Players and pillagers can still equip them on you during this time.

Craft it with iron ingots and a Moondust Matrix:

| Iron | Iron | Iron |
| --- | --- | --- |
| Empty | Iron | Empty |
| Empty | Iron | Moondust Matrix |

## Feral Effigy

Cursed form may struggle with equipment. The Feral Effigy gives you another way to use it (Dragon Survival Style).

Craft this crescent-moon pedestal from cobblestone and Untreated Moondust, then interact with it to infuse equipment that your current form cannot normally use.

Benefit from restricted armor without physically wearing it. Attack with an infused melee weapon or use your infused tools while your main hand is empty.



## Config and Game Rules

Edit `config/ssc-extras.properties` and restart to adjust mod loot chances. Values range
from `0` (disabled) to `1` (guaranteed):

| Setting | Default |
| --- | --- |
| `cursedCollarLootChance` | `0.06666667` (about 6.67% per eligible dungeon chest) |
| `drakeCursePotionLootChance` | `0.25` (25% per pillager outpost chest) |
| `stableReinsLootChance` | `1.0` |
| `stableSaddleLootChance` | `1.0` |
| `stableDrakeReinsDropChance` | `0.085` |
| `stableDrakeSaddleDropChance` | `0.085` |

Natural drake equipment retains its Looting bonus unless its chance is set to `0`.
Player-supplied gear still drops. Existing config values and generated chest contents are preserved.

Use `/gamerule <name> true` or `/gamerule <name> false` to change these world settings immediately:

| Gamerule | Default |
| --- | --- |
| `CursedCollarAutoEquipForCursedPlayers` | `true` |
| `CursedCollarAutoEquipForUncursedPlayers` | `false` |
| `PillagerMountRecruitingForDrake` | `true` |
| `PillagerMountRecruitingForUncursed` | `false` |

Collar rules cover inventory and container auto-equipping, with permanent forms exempt.
The Drake recruiting rule covers all drake stages and players already wearing a cursed
rein or saddle. The uncursed recruiting rule also lets pillagers start equipping uncursed
players and finish their missing pieces while they remain untransformed. Occupied slots
are preserved, and manual equipping remains available.

## Earthen Drake: Body Slam

The permanent drake form gains **Body Slam** on SSC's primary ability key. Rush forward up to three blocks, stopping on an enemy or obstacle, then sweep enemies in front for damage equal to your maximum health. It costs one drumstick (two hunger points) and has a five-second cooldown.

## Some Other Changes

Removed darkness effect during transformation.



Note: Bugs are expected, and feel free to report them!
