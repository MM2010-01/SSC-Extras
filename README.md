## Expanding the Original SSC Transformation Contents.

SSC Extras is an addon for Shape Shifter Curse that expands transformation progression, introduces feralizing collars, and lets transformed players use restricted equipment through the Feral Effigy.

## Instinct-Based Progression

The curse may build before your first transformation. Your instinct gauge is now visible in Original Shifter form, and cursed-creature attacks build instinct toward that creature’s form.
(Changed mod style)

Curse potions add instincts too. Even after transformed, matching creatures and potions continue continue to build your instinct gauge.

Permanent transformation is no longer locked and you may progress through instinct gains. 

Every Earthen Drake transition requires five times its previous instinct: Original Shifter → stage 0,
stage 0 → 1, and stage 1 → 2 cost 5×; stage 2 → permanent costs 50× the normal gauge.

### Cooling Down Your instincts
Now You may cool down your instincts while in the original form and the stage 2 form (before permanent) by not indulging in your instincts.

### Cursed Moonlight

Cursed Moons no longer freeze your instinct gauge. Exposed to it will increase your instinct, hide under a roof! (Or bask in the moonlight, your choice!)

### More Cursed Encounters

Watchout for modded cursed creatures (and the witch). They will continue to spread the curse until your one of them!
SSC Addon creatures compatible.

## Feralizing Collars

Accelerate your transformation with two wearable collars. While worn, they occasionally make you voice your permanent form's native ambient sound, including before the final transformation.

### Feralizing Collar

Crafted from leather surrounded by eight Untreated Moondust, this collar grants +1 instinct per second and doubles positive instinct gains. It can be removed freely.

### Cursed Feralizing Collar

Found in structure loot chests, this purple collar grants +2 instinct per second and quadruples positive instinct gains.

It can bind itself to cursed players when their necklace slot is empty, so watch out while looting if youre transformed. 

Wearing one while uncursed (if you really want to) starts a random form curse, unless the collar has been infused with a specific one.

Collar-triggered animal sounds play at **25%, 50%, 75%, and 100% volume** in stages 0, 1, 2, and permanent form. Before the first transformation, they use 25% volume.

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

Stage 2 paw pads reduce fall damage by **50%**; permanent paw pads reduce it by **75%**. Swim speed is reduced by **15% / 25% / 35%** at stages 1 / 2 / permanent. Permanent drakes use Ravager ambient, hurt and death sounds; SSC's sound key gives a Ravager roar.

Hungry stage 2 and permanent drakes automatically eat meat held in their mouth, using their normal faster eating action. They stop when full.

Metal Cuffs have glowing cyan engravings that turn **orange below 25% durability** and **red below 10%**, independently for each equipped pair.

## Some Other Changes

### Mount Merchants

Small merchant pens can generate roughly 256 blocks from new pillager outposts and woodland mansions, where the terrain is suitable. A neutral, hooded pillager keeps one or two drakes tied to the pen post. Each has a name and price on a sign and an individual listing with a 3D preview and Buy button. Ordinary drakes cost **128 emeralds**; player mounts receive a saved random price of **141–256 emeralds**, and other players can purchase them too. Sold listings and signs clear immediately.

Humans and drakes can offer themselves regardless of their emerald balance. The merchant greets each form differently and gives an introduction before asking **“Are you sure?”**, with **Yes**, **No**, and **No, but actually yes** choices. The last choice briefly pretends to refuse before accepting. Acceptance equips Cursed Reins and a Cursed Saddle, leads you into the pen, and begins the merchant's mount conversion. The merchant feeds ordinary catalysts while the surrounding drakes give harmless instinct attacks, stopping once you finish reaching stage 2 or higher.

The merchant also brings nearby permanent or fully feral drakes, harnessed humans or drakes, and wearers of drake-infused cursed collars or taming collars into the pen. They are listed on arrival and converted as needed, with separate dialogue for captured mounts. The merchant returns to the counter afterward and sprints after escaped mounts to bring them back. On the third recapture, a taming collar is equipped; taming-collared player mounts sell for half price, rounded up to whole emeralds. Other cursed species are refused conversion, and other feral forms cannot trade. Player-owned mounts are protected from automatic recruitment.

When the nearest stable has vacancies, a pillager buyer may arrive, discuss named mounts with the merchant at the counter, and buy ready players first. The buyer enters the pen to collect the purchases, rides one mount home at a sprint, and leads the others alongside. Each mount is delivered through its stall gate and tied inside before ownership is assigned or stable range restrictions apply. NPC purchases leave the last free stall available for a player. Merchant pens require newly generated chunks.

### Drake Stables and Leads

New outposts have three-stall stables with two natural drakes and one empty player stall. Newly generated woodland mansions have larger six-stall stables with five natural drakes and one empty player stall. Each resident has its own saved stall and unique mount name. Existing structures keep their original layout.

Use a Rider's Chest on a natural drake to give it 54 shared storage slots. Sneak-use the drake or press your inventory key while riding to open it. The chest keeps its contents through saving and drops with them if the drake dies.

**Blinding Rein:** craft Cursed Reins with two leather. It normally stays open and works as ordinary Cursed Reins. Pillagers replace a taming-collared player's Cursed Reins with this version. Only a pillager battle ride closes the blinkers, blocking peripheral vision in first person and applying blindness fog in third person. They reopen when the player is returned to the stable; patrols, recalls, and ordinary riding do not close them. The Cleansing Key removes the reins.

Drake players at every stage can be led with a lead and tied to fences. Leashed stage 2 drakes stay on all fours. A pillager can capture a player wearing both Cursed Reins and a Cursed Saddle into the empty player stall from **less than 128 blocks outside an outpost stable boundary**, or **125 blocks at a mansion**, provided it can find complete paths to the player and back to the stall.

Pillagers can also capture **Original Shifter** players wearing both cursed pieces; a vanilla saddle does not qualify. Reined Original Shifters can be led without a saddle. A reined player leashed inside an available stall is registered immediately and receives that outpost's mount name.

Pillagers can open and close the stable's fence gates, including when they spawn inside a stall. Recruitment also works inside another occupied drake stall: the pillager equips the player, leads them out through its gate and into the empty player stall, then closes the gates. An Original Shifter who removes the harness in Creative remains recruitable after returning to Survival with a pending drake curse, when the appropriate recruitment rule is enabled. If a recruit is riding another drake or mount, the keeper dismounts them at close range and leashes them into their stall. Eligible, non-hostile recruits are not shooting targets.

Reins use the face slot with Accessories Curios compatibility. Keepers preserve Lootr stable chests and can hand-feed a drake standing beside the stall fence. Morphscale armor and Morphscale-enabled equipment remain usable through every drake stage.

All-fours drakes can walk up one-block slopes without jumping; fences still block them. A pillager's lead also gives an upright captive one-block stepping when there is room above it. Pillager-supplied leashes drop no lead when broken, including after being tied to a post or reloaded. Player-supplied leads still return normally. Returning riders align with the center of the gate before entering. Once the player is fully inside the stall, the pillager ties the lead, walks out, and closes the gates, during both day and night. Pursuing pillagers follow swimmers at the water surface.

Each natural drake and its stall sign share a randomly selected mount name. The player stall starts with a blank sign. Capture assigns the player to that outpost and gives the stall sign, player name tag, chat name, and tab list the same mount name, selected from [64 mount names](src/main/resources/data/ssc-extras/drake_mount_names.json). The altered player name is italic. Ownership also equips a Drake-infused Cursed Feralizing Collar named after the mount, moving a displaced necklace to inventory or dropping it if full. Ownership and the assigned name survive saving and rejoining.

New woodland mansion stables have **two facing rows of three stalls**, a **seven-block central aisle**, and a higher roof (six blocks of clearance at the eaves, ten beneath the ridge). They generate beside the entrance, with **24 clear blocks beyond the mansion’s side wall**, following all four mansion rotations. Existing generated stables keep their layout and assignments; explore new chunks for the new building. Existing six-stall mansion stables also use the 100-block roaming and 125-block capture ranges.

Each active outpost stable tries to maintain **six pillager guards** while normal mob spawning is enabled. Replenishment and natural pillager spawning stop at **six total pillagers within 64 blocks** or **twenty within 128 blocks** of its boundary, including visitors and raiders. Woodland mansion stables double these counts: **twelve guards**, with caps of **twelve within 100 blocks** and **forty within 125 blocks**. Only the maintained guards are made persistent. Excess unnamed guards retained by older versions regain normal despawning; named pillagers and active raids are preserved. They patrol within 64 blocks of an outpost stable or 100 blocks of a mansion stable and remember their home across reloads. Guards already pursuing a mount count toward the stable's guard population.

During the day, a guard may mount a nearby owned drake for a short **20–40 second territory patrol**, then return it to its own stall. Both NPC drakes and harnessed stage 2/permanent player drakes can patrol. Unridden resident drakes wander inside their home stall during the day. Guards also ride them home when spotted more than eight blocks outside the stable; mounted patrols keep their normal territory range. From dusk, keepers collect available resident drakes throughout the nearby capture range and tether them to their own stall posts, including residents already in their stall. They also tether residents after rides and untie them for the next ride. Daytime patrols turn home at dusk. Patrols give no battle treats and do not count as escapes. Pillagers can sprint their mounts on longer routes, and can run while leading a player home. A keeper chasing on another drake stays mounted while equipping and leashing the recruit, then rides back while towing them. Mounted interaction reach accounts for the drake's body size. A nearby keeper can attach the lead even when the return path is temporarily incomplete, such as on a tree canopy. Mounted escorts slow down and wait if the captive falls behind, and keep riding through the stall gate. Keepers on foot choose **50/50 between a leash and riding** for player captures and recalls, when the player can carry a pillager; Original Shifters and recruits just forced off another mount use a leash.

Stall leashes remain attached through daytime and waking from hay sleep. A pillager removes the tether when mounting, including its empty fence knot. Once untied, owned stage 2 and permanent drake players may roam during the day within **64 blocks of an outpost stable boundary**, or **100 blocks at a mansion**, without being led back. A private warning appears within eight blocks of the edge. Crossing outside sends a private hint with a 30-second cooldown. A nearby guard walks after a drake near the edge and keeps tracking it beyond its initial sight range. Seeing the drake outside gives one spotting hint per excursion; **five continuous seconds of sight** marks the player as trying to escape and calls the other guards. Losing sight resets that timer. Several guards sprint after the same marked drake within the capture range (128 blocks at outposts, 125 at mansions); only the nearest captor displays a lead, and the first to reach the drake can lead it home. Leads follow walkable routes around terrain. Daytime freedom does not open or hold open the gates. Registered players **below stage 2 have no daytime freedom**: keepers leash them back into their stall when caught outside, then force-feed one regular catalyst even before the usual feeding cooldown ends. This feeding lasts three seconds with continuous chewing sounds and particles. From dusk (12000 ticks) until dawn, pillagers also recall owned drakes outside their stall within that same capture range, provided a route is available. A reserved stall remains assigned even while its mount is away. An occupied or otherwise reserved destination stall cannot capture another player.

Pillagers on foot also shoot monsters targeting a harnessed player, including creepers before they attack. Pillagers facing nearby non-illager monsters or hostile players choose different, random mounts belonging to their own outpost. Native stable drakes and harnessed stage 2 or permanent player drakes can be selected. Sleeping drakes are left asleep. Riders switch to substantially closer enemies along the route and immediately reconsider a target that dies; if no enemy remains before combat begins, the trip is cancelled. The pillagers open the gates, ride into combat using their crossbows, then return each mount to its own stall, dismount, leave, and close the gates. After participating in combat and winning, each rider has a **30% chance** to reward its mount; player treats increase with hunger lost during the ride. Battle rides do not count as escapes.

The outpost remembers successful escape recaptures under the same mount name. The **third recapture** gives a private last-chance warning. On the **fourth recapture**, a pillager fits a **Cursed Taming Collar** bearing that mount name and restores missing cursed reins or saddle. This black metal neck cuff has purple engravings and the cursed collar's feralizing effects, but never auto-equips from inventory. It moves other neck accessories to inventory, or drops them if full. It keeps stage 2 drakes on all fours and prevents opening fence gates, breaking or placing blocks, and directly untying your own leash. Right-clicking your leash knot gives a **5% chance** to struggle free. The collar remains bound after permanent transformation; a **Cleansing Key** removes it. Returning voluntarily or after battle does not increase the counter, and a new outpost/name starts a new record.

Keepers repair broken stable blocks and fences, restore stall signs, and remove foreign blocks. A drake receives warnings for the first two witnessed destructions of stable blocks or placements of irrelevant blocks. Tapping a block or failing to destroy it does not count. The **third witnessed violation** arranges a **four-paw shoeing ritual**; the count survives rejoining. Two keepers hold the player on their back on the hay while the third grabs each limb and fits its shoe. Private hints explain each step.

**Cursed Iron Drake Shoes** are the same three-clawed pair for both the hand and feet accessory slots. The ritual equips two identical pairs, each carrying **Curse of Binding** and the same drake curse as the reins and saddle; an already Soulbound mount's shoes become Soulbound too. Hand shoes force mouth carrying even at stages 0 and 1, prevent tools and block breaking, placement, or interaction except hay, and add **5 damage** to empty-paw attacks (the iron sword attack bonus). Feet shoes reduce sprinting and sprint-jump hunger exhaustion by **30%**. Shoeing preserves the player's current transformation stage and mind. Fitted shoe models cover the original shifter, including slim arms and first-person hands, and every drake stage.

For quick testing, operators can use `/ssc-extras ritual irondrakeshoefitting [player]`, `/ssc-extras ritual soulbinding [player]`, or `/ssc-extras ritual feralization [player]`. These commands bring the player and three keepers to the assigned stall and start the real ritual, including in Creative mode. Missing keepers are supplied. No stall means the command reports that the ritual cannot be performed. An untransformed player starts at drake stage 0; existing drake stages follow the chosen ritual's normal outcome. Test commands bypass service and escape requirements without changing those counters.

All SSC Extras commands accept an optional final player name or single-player selector such as `@p` or `@s`. Omitting it targets the player running the command; a command block or another non-player source defaults to `@p` at its execution position. No matching player produces the normal Minecraft command error. Operator permission level 2 is required.

`/ssc-extras purge-cursed-equipment [player]` unequips every worn accessory supported by the Cleansing Key: Cursed Feralizing and Taming Collars, Cursed and Blinding Reins, Cursed Saddles, and Iron Drake Shoes on both front and hind paws. It drops the items with their saved data intact and grants 30 seconds of Curse Cleansed, without needing or consuming a key. Your form and instinct are preserved.

While keepers restrain the player for any ritual, the view switches to a free camera: mouse to look, movement keys to fly horizontally, jump/sneak to rise or descend, and sprint to move faster. The previous perspective returns when restraint ends. Leawind's camera yields during this view without changing its settings. In first person while ridden by a pillager, mouse look remains independent, with a gentle pull toward the rider's heading while moving.

Soulbinding repeatedly feeds SSC's regular catalyst until the native transformation reaches at least stage 2, including command-started rituals; feralization feeds the full beastization catalyst. Each feeding lasts three seconds with normal chewing cadence. Ritual transformation cleanup restores movement and instinct processing, including recovery on rejoining for previously affected bound or feral drakes.

Operators can clear these effects independently with `/ssc-extras clear PermanentMount [player]` or `/ssc-extras clear Feralization [player]`; lowercase names also work. PermanentMount removes the soul bond and carried/worn Soulbound enchantments, restores the previous respawn point, and resets service progress. Feralization removes the saved feral mind and temporary catalyst effect, stops feral takeovers, and resets escape recaptures. Clearing an effect cancels its active ritual. Neither command requires a stall or changes the current form or equipment's other curses.

The **sixth escape recapture** at the same stable triggers the full feralization punishment. Three pillagers with routes to the player and stall collect and leash the player home. Selected attendants stay assigned to the ritual; an unavailable or unreachable attendant is replaced by another nearby keeper when possible. The guide starts collecting the player as soon as they are within reach; the other attendants can catch up at the stall. A mounted guide stays on its drake until the player reaches the hay. Their leader feeds a full beastization catalyst, then chants while the other two hold the player. Completing the ritual gives the player a permanent drake body and a feral mind. The translucent figure represents the mind being feralized. Feralization does not grant Soulbound equipment, hay respawn, or a permanent mount bond. Without a separate soulbinding ritual, death returns the player to Original Shifter and clears the feral mind. All three keepers must remain present.

Fully feral players can break blocks, drop items, eat any food item, and use hay for sleeping. Other item uses, block interactions, crafting and manual equipment changes are blocked. Separate iron-shoe and taming-collar restrictions still apply. Raw food is named **yummy!** (**好吃！** in Simplified Chinese); other food is named **Food?** (**食物？**), with no extra tooltip. Non-food item names use Minecraft's gibberish glyphs. Hints, chat and other in-game text are scrambled too, except the mount name and **meat**. Item counts, XP levels, system menus and the AI takeover notice remain readable. The drake occasionally takes over the player's body for **5–30 seconds**, wandering, seeking raw food or returning to its hay to sleep; the hotbar turns grey and player mining pauses during these episodes. The feral mind survives rejoining. Only a separately Soulbound player keeps it through death; fully curing the drake form also clears it.

Both permanent feralization and the temporary Total Feralized effect keep every drake stage quadrupedal, with matching posture, hitbox and eye height, and held items carried in the mouth. Fully feral players retain mouth carrying through partial recovery, including in normally upright forms, until the fourth Sentient Catalyst restores their posture. Temporary posture ends when the effect expires.

Ritual hints remain readable while the keepers are performing the ritual, including after catalyst feeding. After release, text stays clear for five seconds, then gradually becomes gibberish over ten seconds. The mount name and **meat** remain readable throughout.

While a drake-cursed player is inside any generated stable stall, **positive instinct gains increase by 50%**, including passive buildup, collar buildup, and instinct actions. This also applies before the first drake transformation. Instinct loss is unchanged, and Metal Cuffs still suppress the increased gain.

After a captured player first transforms into a drake, a nearby pillager explains the stable rules: stay within the stable's roaming range (64 blocks at outposts, 100 at mansions), do not escape, and be in the assigned stall for the keeper's check after midnight. Service progress continues while roaming. A local keeper checks the stall once per night after midnight; the ten-day promise breaks only if the player is not seen there during that check. Each player hears the rules once per stable, remembered across releases, recaptures, and reloads. Pillagers speak privately to their mount in **gray**, using its assigned mount name. They announce mounting for guard patrols, roaming, battles, and recalls, call out escapes, and speak when catching, tethering, feeding, or rewarding the mount.

When an owned mount is in its stall with fewer than 10 food points (five drumsticks), a nearby pillager offers 1–2 random pieces of raw beef, pork, mutton, rabbit, or chicken by throwing them toward the player. Pillagers share a ten-second feeding delay for that mount. At stages 0–1, a player with **6 food points or fewer (three drumsticks)** who still has raw meat in their inventory is fed by hand. One pillager enters the stall and feeds a bite every two seconds until full, using the stored meat first and supplying more if needed. Each bite gives the normal food benefits plus **2 base instinct**, subject to the usual instinct modifiers. Hand feeding pauses while the player uses an item and ends if they leave the stall, change to a later stage, or combat interrupts the pillager.

Ownership clears when the player is in **Original Shifter form and at least 16 blocks outside the stable boundary**. Returning to original form near the stable, removing the gear, or leaving while still transformed does not clear ownership. Release clears the stall sign and restores the previous displayed name. Capture by another outpost transfers ownership and clears the old sign. **Bond of the Beast** is optional: its player ownership takes precedence, releases the outpost claim, and preserves that add-on's own naming behavior.

All four drake forms can sleep at night by right-clicking a flat horizontal patch of at least **2×2 hay bales**, with enough clear space above it. Normal waking, leaving sleep, and multiplayer night skipping apply. Sneaking lets you keep building on hay without starting sleep.

Removed darkness effect during transformation.



Note: Bugs are expected, and feel free to report them!
