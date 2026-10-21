# VoidWeapon

VOID WEAPON is a Paper plugin for the VOID SMP survival server. It provides six PersistentDataContainer-identified legendary weapons, configurable abilities, secure arrow storage, forge menus, timed crafting, recipe unlocks, persistent forge trust, and restart-recoverable reward jobs.

## Compatibility and build

- Target server/API: Paper 26.3 (`26.3.build.200-stable` API dependency).
- Java: 25.
- Build system: Gradle Kotlin DSL; GitHub Actions uses Gradle 9.1.0 and Temurin Java 25.
- Paper's official setup guide documents the 26.3 API coordinate and Java 25 toolchain. Gradle's compatibility table lists Java 25 support starting with Gradle 9.1.
- Build/test command used by CI: `gradle clean build`. CI uploads `build/libs/VoidWeapon-1.2.0.jar` only after build and tests succeed.
- No wrapper scripts or wrapper JAR are included; a generated wrapper could not be verified in this project environment.

In Kodari, use **Compile** in the top bar, then **Compile** in the build window. Download the JAR from the successful build result. Install it in the Paper server's `plugins` directory and restart the server. Configuration is created on first enable.

## Weapons and abilities

| Weapon | Abilities |
|---|---|
| AUTO BOW | Auto Fire (8 stored arrows), Piercing Shot (capped piercing projectile), Phantom Volley (stored arrows) |
| WIND BLADE | Phantom Dash, Cyclone Cut, Sky Render |
| THUNDER SPEAR | Thunder Throw (trident projectile), Chain Lightning, Storm God |
| VOID CRUSHER | Void Smash, Gravity Break, Collapse |
| THE BLADE OF DEMON | Demon Slash, Crimson Burst, Hellfire Domain |
| GOD SLAYER | God Strike, Divine Judgment |

All abilities have server-side mechanics. Damage, cooldowns, ranges, projectile parameters, target caps, knockback, and timed effects are read from `config.yml` and clamped to safe bounds. Direct damage uses Bukkit entity damage events so server protection plugins can cancel damage; this plugin does not ship a hard WorldGuard dependency or custom region adapter. Lightning effects are visual-only and do not ignite blocks.

## Default controls

- AUTO BOW: sneak + left-click opens Arrow Storage; right-click activates Auto Fire.
- Other weapons: right-click activates the currently selected ability (initially the first ability in that weapon's list).
- `/voidweapon ability <weapon> <ability>` directly activates an ability and makes it the selected ability after successful activation. This is the fallback for abilities not assigned to the default input.
- Controls can be enabled/disabled under `general.controls`.

## Arrow Storage

The storage GUI is opened only by the AUTO BOW trigger. Click **Deposit arrows** to store eligible arrows from the player's main inventory, or **Withdraw arrows** to return stored stacks. Storage defaults to 2,304 arrows and preserves normal, spectral, and tipped arrow item metadata when configured. Auto Fire reserves exactly eight stored arrows before starting; Phantom Volley reserves its configured count. Neither ability reads ordinary inventory arrows. A burst refunds reserved arrows for projectiles that were not spawned if the burst is interrupted or spawning fails.

The GUI is session-bound, all inventory interactions are cancelled, and button items carry a server-side PDC token. Arrow storage is persisted in the configured YAML state file.

## Forge and crafting

Place the configured forge block (default `LODESTONE`), look at it, then use `/start`. The first player to open an unclaimed forge becomes its owner. Owners can grant or revoke trust for that forge. Recipe unlocks are independent of trust and are stored per player UUID. Ingredients are checked and removed once when a job starts. The default job duration is 1,200 seconds from the persisted start timestamp. Jobs continue across restart; ready rewards remain queued if the owner is offline or their inventory is full. Use `/voidweapon crafting` to inspect a job. Cancelling a job does not refund consumed materials.

Recipes, forge material, duration, trust policy, GUI capacity, weapon stats, and messages are configurable. Shaped recipes are defined as 3x3 strings in `crafting.recipes` with a symbol-to-material mapping; each non-space symbol consumes one item.

## Commands

| Command | Purpose |
|---|---|
| `/start` | Open a configured forge while looking at it |
| `/voidweapon help` | Show help |
| `/voidweapon give <player> <weapon> [amount]` | Give online player weapon(s) |
| `/voidweapon unlock recipe <weapon>` | Unlock own recipe |
| `/voidweapon unlock recipe <player> <weapon>` | Admin recipe unlock |
| `/voidweapon trust <player>` / `/voidweapon untrust <player>` | Manage trust at the targeted forge |
| `/voidweapon list` | List weapons and IDs |
| `/voidweapon reload` | Reload configuration |
| `/voidweapon crafting [player]` | Show crafting status |
| `/voidweapon cancelcraft [player]` | Cancel own job or administer another player's job |
| `/voidweapon cooldowns [player]` | Show active cooldowns |
| `/voidweapon version` | Show plugin and target version |
| `/voidweapon ability <weapon> <ability>` | Directly activate and select an ability |

Weapon IDs and ability IDs use lowercase underscore form, for example `auto_bow` and `phantom_volley`.

## Permissions

- `voidweapon.admin` — full administrative commands; default false.
- `voidweapon.use` — use weapons and abilities; default true.
- `voidweapon.craft` — use forge crafting; default true.
- `voidweapon.recipe.unlock` — unlock your own recipes; default false.
- `voidweapon.trust` — manage forge trust when you own the forge; default false.

Permissions are checked in command and ability handlers, not just hidden by tab completion. Admin permission is not automatically granted to operators by this plugin's permission defaults.

## Persistence, testing, and limitations

`plugins/VoidWeapon/data.yml` stores ammunition, unlocks, trust, jobs, and reward statuses. This project contains unit tests for weapon ID validation and UUID/ability cooldown isolation. Server-dependent mechanics (inventory events, projectile counts/hits, Paper damage/protection events, GUI interactions, and restart/reward integration) require integration testing on a real Paper server; they have not been run here.

The project environment did not provide Gradle execution, so no compile, test, JAR inspection, or ZIP packaging was performed. The GitHub Actions workflow performs the real `clean build` and fails on errors. The implementation does not include a dedicated WorldGuard adapter, particle/sound customization, offline-name resolution for admin commands, or automated server integration tests. Region protections depend on their Bukkit event hooks for entity damage and teleport cancellation. Verify those integrations and the combat balance on a staging server before production use.

## Troubleshooting

- If the plugin fails to load, verify the server is Paper 26.3 running Java 25 and inspect the startup log for API or config errors.
- If `/start` reports no forge, target a block whose material matches `crafting.forge.material`.
- If crafting is unavailable, check the player's `voidweapon.craft` permission, recipe unlock, forge trust, and configured materials.
- If a reward does not fit, clear inventory space; it remains queued for a later delivery attempt.
- If an ability does not activate, confirm the correct PDC-marked weapon is in the main hand, the ability is unlocked by the player-held item state (not recipe unlock), the game mode is allowed, and the ability cooldown has elapsed.