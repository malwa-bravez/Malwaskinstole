# MalwaSkinStole 1.0.0

Paper 1.20.4+ (built against the 1.20.4 API, Java 17). Command: `/s`.

## Build

    mvn clean package        # -> target/MalwaSkinStole-1.0.0.jar

Drop the jar into `plugins/`. **This source has not been compiled yet** — it was written in a
sandbox with no access to Maven Central / PaperMC repos. Expect to fix a few small API mismatches
on first build, and test on a throwaway server first (the laser deletes terrain).

## What's implemented

- **Skin theft**: kill counts per victim live in the killer's PDC; the 10th kill gives the killer a
  Stolen Skin item (player head with the victim's texture). Right-click it to equip/unequip, or `/s skin toggle`.
- **Permadeath**: after a player's skin is stolen, 7 deaths to players = ban (via the server ban list) and
  their skin drops at the death location.
- **Secondary inventory**: `/s inv` (27 slots, saved in PDC).
- **Wizard Warden**: 1150 HP; Slowness/Weakness cut its damage; Regeneration drops max HP to 780 and
  drains 20-30 per regen tick; 2 Wardens per 400 HP lost (2-4 min lifetime); at 29% HP it charges and fires a
  100x30 block-mining laser; drops 2-5 Revive Tokens. Boss bar included.
- **`/s spawn wizardwarden`**: locates the nearest Ancient City, carves a sealed deepslate chamber in it, spawns the boss.
- **Crafting**: 9 Tokens -> Star; Chair recipe exactly as specified; max 7 chairs in the world.
- **Revival Chair**: crouch + right-click registers it to you; right-click with a skin to start the ritual
  (particle beam to build height, 1000-block notification, raid mobs that target only the reviver,
  Endermen that reach for the skin and always fail). Surviving the countdown unbans the skin's owner.
- **Commands**: `/s ban`, `/s unban`, `/s locate revivechair`, `/s spawn wizardwarden`, plus `/s give`, `/s inv`, `/s skin`.

## Interpretations / limitations

- The "beacon beam" is a particle column (a real beacon beam is blocked by solid blocks).
- No ProtocolLib; the laser is block-slicing + particles done server-side, spread over ticks.
- The boss is only created by `/s spawn` (it does not generate naturally in worldgen).
- Skin changes use Paper's `setPlayerProfile`; other players see it immediately, your own view may need a relog.
- "20-30 damage per tick" is applied per Regeneration heal tick (every 25-50 game ticks), not every game tick.
- Ritual requires the skin's owner to be banned (`revival-chair.ritual.require-banned`).
- Attribute constants (`GENERIC_MAX_HEALTH`) were renamed in 1.21.3, so this jar targets 1.20.4 - 1.21.1.

All numbers are in `config.yml`.
