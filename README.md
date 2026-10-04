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

### Drake Stables and Leads

Newly generated stables have three stalls: two with natural drakes and one empty stall, with solid log pillars extending to the floor. Existing stables keep their original layout.

Use a Rider's Chest on a natural drake to give it 54 shared storage slots. Sneak-use the drake or press your inventory key while riding to open it. The chest keeps its contents through saving and drops with them if the drake dies.

Stage 2 and permanent drake players can be led with a lead and tied to fences. Leashed stage 2 drakes stay on all fours. A pillager can capture a player wearing both Cursed Reins and a Cursed Saddle into the empty third stall from **less than 128 blocks outside the stable boundary**, provided it can find complete paths to the player and back to the stall.

Pillagers can also capture **Original Shifter** players wearing both cursed pieces; a vanilla saddle does not qualify.

Pillagers can open and close the stable's fence gates, including when they spawn inside a stall. Recruitment also works inside another occupied drake stall: the pillager equips the player, leads them out through its gate and into the empty player stall, then closes the gates. An Original Shifter who removes the harness in Creative remains recruitable after returning to Survival with a pending drake curse, when the appropriate recruitment rule is enabled. Eligible, non-hostile recruits are not shooting targets.

A pillager's lead lets the player step onto a one-block raised floor when there is room above it. Once the player is fully inside the stall, the pillager ties the lead, walks out, and closes the gates, during both day and night.

Each natural drake and its stall sign share a randomly selected mount name. The player stall starts with a blank sign. Capture assigns the player to that outpost and gives the stall sign, player name tag, chat name, and tab list the same mount name, selected from [64 mount names](src/main/resources/data/ssc-extras/drake_mount_names.json). Ownership and the assigned name survive saving and rejoining.

Owned drake players are untied during the day and may roam within **64 blocks of the stable boundary** without being led back. A private warning appears within eight blocks of the edge. Crossing outside sends a private hint with a 30-second cooldown. A pillager that sees the drake outside gives one spotting hint per excursion; **five continuous seconds of sight** marks the player as trying to escape. Losing sight resets that timer. A pillager that sees a marked drake attempts to leash it back within the 128-block capture range. Daytime freedom does not open or hold open the gates. Captured **Original Shifters have no daytime freedom** before their first transformation: they stay tied and nearby pillagers recall them during the day too. At night, pillagers also recall owned drakes outside their stall within the same 128-block capture range, provided a route is available. A reserved stall remains assigned even while its mount is away. An occupied or otherwise reserved destination stall cannot capture another player.

Pillagers facing nearby non-illager monsters or hostile players choose different, random mounts belonging to their own outpost. Native stable drakes and harnessed stage 2 or permanent player drakes can be selected. The pillagers open the gates, ride into combat using their crossbows, then return each mount to its own stall, dismount, leave, and close the gates. After victory, each rider has a **30% chance** to reward its mount; player treats increase with hunger lost during the ride. Battle rides do not count as escapes.

The outpost remembers successful escape recaptures under the same mount name. On the **fourth recapture**, a pillager fits a **Cursed Taming Collar** and restores missing cursed reins or saddle. This black metal neck cuff has purple engravings and the cursed collar's feralizing effects, but never auto-equips from inventory. It moves other neck accessories to inventory, or drops them if full. It prevents opening fence gates, breaking blocks, and directly untying your own leash. Right-clicking your leash knot gives a **5% chance** to struggle free. The collar remains bound after permanent transformation; a **Cleansing Key** removes it. Returning voluntarily or after battle does not increase the counter, and a new outpost/name starts a new record.

When an owned mount is in its stall with fewer than 10 food points (five drumsticks), a nearby pillager offers 1–2 random pieces of raw beef, pork, mutton, rabbit, or chicken by throwing them toward the player. Pillagers share a ten-second feeding delay for that mount.

Ownership clears when the player is in **Original Shifter form and at least 16 blocks outside the stable boundary**. Returning to original form near the stable, removing the gear, or leaving while still transformed does not clear ownership. Release clears the stall sign and restores the previous displayed name. Capture by another outpost transfers ownership and clears the old sign. **Bond of the Beast** is optional: its player ownership takes precedence, releases the outpost claim, and preserves that add-on's own naming behavior.

All four drake forms can sleep at night by right-clicking a flat horizontal patch of at least **2×2 hay bales**, with enough clear space above it. Normal waking, leaving sleep, and multiplayer night skipping apply. Sneaking lets you keep building on hay without starting sleep.

Removed darkness effect during transformation.



Note: Bugs are expected, and feel free to report them!
