#!/usr/bin/env python3
"""
Generate DESIGN.md §7.3 — the discovery path: the whole advancement tree, the hidden recipe-unlock
layer beneath it, and the `advancements.heartstead.*` lang block that names them.

    python3 tools/gen_advancements.py

CONVENTIONS.md §6 applies exactly as it does to the texture scripts: **this script is the artifact
and the JSON is build output.** Retitle an advancement or move a branch here and re-run it; do not
hand-edit the committed JSON, because the next run overwrites it.

Two layers, both required and neither sufficient alone:

  advancement/*.json          the visible tree — one tab, rooted at heartstead:root, which is
                              granted by minecraft:tick so the tutorial exists before the player
                              owns anything (§7.3).
  advancement/recipes/*.json  hidden, display-less, and the only reason Heartstead's recipes show
                              up in the recipe book at all — a recipe no advancement grants is
                              never unlocked. Staged per §7.3's table so the book teaches.

**Nothing here grants an item, a Sigil or experience.** §7.3's whole claim to existing before
playtesting is that it pays nothing, so `rewards` never carries anything but `recipes`.
"""
import json
import os
import shutil

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
ADV = os.path.join(ROOT, "src/main/resources/data/heartstead/advancement")
RECIPE_DIR = os.path.join(ROOT, "src/main/resources/data/heartstead/recipe")
LANG = os.path.join(ROOT, "src/main/resources/assets/heartstead/lang/en_us.json")

# Vanilla's own tab backgrounds. A Heartstead one would be a fifth placeholder texture (commit
# c0251d5) pretending to be finished art; stone reads correctly and costs nothing.
BACKGROUND = "minecraft:gui/advancements/backgrounds/stone"


# ---------------------------------------------------------------------------------------------
# criteria helpers
# ---------------------------------------------------------------------------------------------

def has(*items):
    """minecraft:inventory_changed on any of `items` — "you are holding one of these"."""
    return {
        "trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": list(items)}]},
    }


def has_all(items):
    """minecraft:inventory_changed on *every* one of `items` — "you can craft this right now".

    Each entry in an inventory_changed `items` list is a separate predicate that must find a
    matching stack, so a list of one-item predicates is an AND, where has()'s single predicate
    holding several items is an OR. Counts are deliberately not checked: the predicate matches
    per-stack, so requiring 8 deepslate bricks would fail a player holding two stacks of four.
    """
    return {
        "trigger": "minecraft:inventory_changed",
        "conditions": {"items": [{"items": item} for item in items]},
    }


def hs(path, **conditions):
    """One of the heartstead: criterion triggers registered by HsTriggers."""
    criterion = {"trigger": f"heartstead:{path}"}
    if conditions:
        criterion["conditions"] = conditions
    return criterion


def crafted(recipe):
    return {"trigger": "minecraft:recipe_crafted", "conditions": {"recipe_id": recipe}}


def stored_enchantment(enchantment):
    return {
        "trigger": "minecraft:inventory_changed",
        "conditions": {
            "items": [{
                "items": "minecraft:enchanted_book",
                "predicates": {"minecraft:stored_enchantments": [{"enchantments": enchantment}]},
            }],
        },
    }


# ---------------------------------------------------------------------------------------------
# the visible tree
#
# (name, parent, icon, type, title, description, {criterion name: criterion})
# Every entry with more than one criterion is an OR — "any of these counts" — which is what
# `requirements` as a single group means. Nothing in this tree needs an AND except three_ways,
# which is spelled out below.
# ---------------------------------------------------------------------------------------------

TREE = [
    (
        "root", None, "heartstead:sigil", "task",
        "Heartstead",
        "Explore, fight and build. Sigils drop from doing those things, and everything here is "
        "bought with them — storage, farms, and hearts. Nothing here is built from redstone.",
        {"joined": {"trigger": "minecraft:tick"}},
    ),

    # --- the spine (§1) -----------------------------------------------------------------------
    (
        "sigil", "root", "heartstead:sigil", "task",
        "Something Worth Keeping",
        "Find a Sigil. They come from chests, bosses and the occasional mob — never from a farm. "
        "This one item feeds all three of Heartstead's systems, so what you spend it on is the "
        "whole choice.",
        {"found": has("heartstead:sigil")},
    ),
    (
        "core_sigil", "sigil", "heartstead:core_sigil", "task",
        "The Cheapest of the Three",
        "Craft a Core Sigil. It is the cheapest thing a Sigil becomes, on purpose: replacing a "
        "farm should be the first thing you can afford.",
        {"crafted": has("heartstead:core_sigil")},
    ),
    (
        "heart_sigil", "sigil", "heartstead:heart_sigil", "task",
        "Or Buy Survival",
        "Craft a Heart Sigil. Use it and you keep the heart — until you die, which costs one.",
        {"crafted": has("heartstead:heart_sigil")},
    ),
    (
        "vault_sigil", "sigil", "heartstead:vault_sigil", "task",
        "Or Buy Storage",
        "Craft a Vault Sigil. The Vault itself is free — this is what makes it bigger, makes it "
        "reach further, and puts it back if you move the Anchor. It is the most expensive of the "
        "three.",
        {"crafted": has("heartstead:vault_sigil")},
    ),
    (
        "three_ways", "sigil", "heartstead:sigil", "goal",
        "Three Ways to Spend It",
        "Hold a Core Sigil, a Heart Sigil and a Vault Sigil at the same time. Every Sigil you find "
        "is one of these three and not the other two — that tension is the point.",
        {
            "core": has("heartstead:core_sigil"),
            "heart": has("heartstead:heart_sigil"),
            "vault": has("heartstead:vault_sigil"),
        },
    ),

    # --- the Vault (§2) -----------------------------------------------------------------------
    (
        # Off root, not off vault_sigil: §2.1's first Anchor activates free and the Satchel is a
        # T1 vanilla craft, so nothing from here to vault_deposited costs a Sigil. Hanging it off
        # the Sigil said the opposite, and the branch below is where the currency actually starts.
        "vault_anchor", "root", "heartstead:vault_anchor", "task",
        "Somewhere to Come Back To",
        "Craft a Vault Anchor. One per world, and it holds everything the Vault is: its contents, "
        "its size and how far it reaches. It costs no Sigil and the first activation is free — "
        "place it where your base is going to stay.",
        {"crafted": has("heartstead:vault_anchor")},
    ),
    (
        "vault_activated", "vault_anchor", "heartstead:vault_anchor", "task",
        "The Vault Wakes",
        "Activate the Anchor. The first one a world ever has is free; replacing it later costs a "
        "Vault Sigil, which is what stops the Vault being something you carry around.",
        {"activated": hs("vault_activated")},
    ),
    (
        "satchel", "vault_activated", "heartstead:satchel", "task",
        "Send It Home",
        "Craft a Satchel. It deposits into the Vault from anywhere, in any dimension, for free — "
        "no more walking home because your inventory filled up. It cannot take anything back out.",
        {"crafted": has("heartstead:satchel")},
    ),
    (
        "vault_deposited", "satchel", "minecraft:chest", "task",
        "Straight to the Vault",
        "Put something in the Vault. Deposit is the safe verb: unlimited range, no cost, granted "
        "the moment you have a Satchel.",
        {"deposited": hs("vault_deposited")},
    ),
    (
        "vault_pouch", "satchel", "heartstead:vault_pouch", "task",
        "The Other Verb",
        "Craft a Vault Pouch. It adds withdrawal — the powerful half — with search and sort. "
        "Spending a Vault Sigil on your own Pouch is one not spent on the world's Vault.",
        {"crafted": has("heartstead:vault_pouch")},
    ),
    (
        "vault_withdrew", "vault_pouch", "minecraft:hopper", "task",
        "Restock From Anywhere",
        "Take something out of the Vault remotely. This is the verb the ladder sells, and it only "
        "works where the Anchor's reach covers you.",
        {"withdrew": hs("vault_withdrew")},
    ),
    (
        "vault_capacity", "vault_activated", "heartstead:vault_sigil", "task",
        "Room to Grow",
        "Spend a Vault Sigil on capacity at the Anchor. There is no ceiling on this one — it keeps "
        "taking Sigils for as long as you keep finding them.",
        {"bought": hs("vault_upgraded", kind="capacity")},
    ),
    (
        "reach_overworld", "vault_activated", "heartstead:overworld_vault_sigil", "task",
        "Overworld-Wide",
        "Buy Overworld reach with a Sigil crafted against an Echo Shard. The proof item is the "
        "gate: you cannot buy reach into somewhere you have never been.",
        {"bought": hs("vault_upgraded", kind="overworld_reach")},
    ),
    (
        "reach_nether", "reach_overworld", "heartstead:nether_vault_sigil", "task",
        "Through the Portal",
        "Buy Nether reach. Reach tiers are per-dimension and not cumulative — each one names the "
        "place it unlocks.",
        {"bought": hs("vault_upgraded", kind="nether_reach")},
    ),
    (
        "reach_end", "reach_nether", "heartstead:end_vault_sigil", "challenge",
        "The Long Reach",
        "Buy End reach with a Sigil crafted against Dragon's Breath. Your whole storage, anywhere "
        "you can stand.",
        {"bought": hs("vault_upgraded", kind="end_reach")},
    ),
    (
        "linked_funnel", "vault_activated", "heartstead:linked_funnel", "task",
        "Set and Forget",
        "Craft a Linked Funnel. Hopper-speed, straight into the Vault from any farm anywhere — or "
        "one filtered item back out, if the reach covers where it stands.",
        {"crafted": has("heartstead:linked_funnel")},
    ),

    # --- cores (§4, §5) -----------------------------------------------------------------------
    (
        "primed_core", "core_sigil", "heartstead:primed_verdant_core", "task",
        "A Core With Nothing In It",
        "Craft a Primed Core. It does nothing yet. Carry it while you do the thing you want it to "
        "replace, and it learns that thing.",
        {"crafted": has(
            "heartstead:primed_soul_core",
            "heartstead:primed_verdant_core",
            "heartstead:primed_pastoral_core",
            "heartstead:primed_lithic_core",
        )},
    ),
    (
        "classic_core", "primed_core", "heartstead:primed_soul_core", "task",
        "The Specialists",
        "Craft one of the eleven classic cores — guardian, wither skull, ender, shulker, raid, "
        "barter and the rest. Each replaces a farm that used to need a schematic.",
        {
            name: crafted(f"heartstead:{name}") for name in [
                "guardian_core", "wither_skull_core", "ender_core", "shulker_core", "ominous_core",
                "barter_core", "golem_core", "slime_core", "tidal_core", "geode_core", "apiary_core",
            ]
        },
    ),
    (
        "core_attuned", "primed_core", "heartstead:verdant_core", "task",
        "It Learns From You",
        "Finish attuning a core. Mine the block, farm the crop or fight the mob enough times while "
        "carrying it, and it becomes a finished Core locked to exactly that.",
        {"attuned": hs("core_attuned")},
    ),
    (
        "core_housing", "core_attuned", "heartstead:verdant_planter", "task",
        "Somewhere to Run It",
        "Craft a housing. Each family has its own — Planter, Paddock, Quarry Node, Soul Cage — and "
        "the housing decides which cores it will take.",
        {"crafted": has(
            "heartstead:verdant_planter",
            "heartstead:paddock",
            "heartstead:quarry_node",
            "heartstead:soul_cage",
        )},
    ),
    (
        "core_socketed", "core_housing", "heartstead:soul_cage", "task",
        "Retire the Farm",
        "Socket an attuned core. It produces whether or not you are there, whether or not the "
        "chunk is loaded — and one core per target per world, so there is nothing to scale by "
        "building more.",
        {"socketed": hs("core_socketed")},
    ),
    (
        "core_yield", "core_socketed", "minecraft:wheat", "task",
        "Income",
        "Collect what a core made. Point a Linked Funnel at the housing and you never have to "
        "again.",
        {"collected": hs("core_yield_collected")},
    ),
    (
        "core_tier_iii", "core_socketed", "minecraft:netherite_scrap", "challenge",
        "Deep Roots",
        "Run a tier III core. Tiering is the only way to make a core faster — six times the base "
        "rate, and the entire scaling curve the pack has.",
        {"socketed": hs("core_socketed", tier={"min": 3})},
    ),

    # --- the Codex (§3) -----------------------------------------------------------------------
    (
        "codex", "root", "heartstead:codex", "task",
        "Written Down",
        "Craft a Codex. It costs no Sigil — enchanting is the one system the currency does not "
        "gate. Feed it a book and it remembers the enchantment forever.",
        {"crafted": has("heartstead:codex")},
    ),
    (
        "codex_archived", "codex", "minecraft:enchanted_book", "task",
        "Filed Away",
        "Archive an enchantment. Your archive follows you, not the block, so any Codex anywhere "
        "shows you the same library.",
        {"archived": hs("codex_archived")},
    ),
    (
        "tome", "codex_archived", "heartstead:sealed_tome", "task",
        "Take a Copy",
        "Seal a Tome against something in your archive. That is a copy of an enchantment you "
        "already own, on demand, at a fixed price.",
        {"sealed": has("heartstead:sealed_tome")},
    ),
    (
        "villager_taught", "tome", "minecraft:emerald", "goal",
        "Teach the Librarian",
        "Teach a librarian from a Sealed Tome. The trade sticks — through level-ups, restocks and "
        "reloads — so a librarian you taught stays taught.",
        {"taught": hs("villager_taught")},
    ),
    (
        "abundance", "codex", "minecraft:enchanted_book", "task",
        "Abundance",
        "Find the Abundance treasure enchantment. It multiplies what a block drops, and it is one "
        "of the two enchantments the pack adds.",
        {"found": stored_enchantment("heartstead:abundance")},
    ),
    (
        "kiln_touch", "codex", "minecraft:furnace", "task",
        "Kiln Touch",
        "Find Kiln Touch. Blocks come out of the ground already smelted — no furnace, no fuel.",
        {"found": stored_enchantment("heartstead:kiln_touch")},
    ),

    # --- lives (§6) ---------------------------------------------------------------------------
    (
        "heart_gained", "heart_sigil", "minecraft:golden_apple", "task",
        "One More Hit",
        "Use a Heart Sigil and keep the heart. Dying costs you one back, down to a floor of five, "
        "so hearts are something you hold rather than something you own.",
        {"gained": hs("heart_level", hearts={"min": 11})},
    ),
    (
        "heart_max", "heart_gained", "minecraft:enchanted_golden_apple", "challenge",
        "Twenty Hearts",
        "Reach the cap: twenty hearts, ten of them bought a Sigil at a time.",
        {"capped": hs("heart_level", hearts={"min": 20})},
    ),
]


# ---------------------------------------------------------------------------------------------
# the hidden recipe-unlock layer (§7.3's staging table)
#
# recipe -> the item whose possession unlocks it. A recipe with no advancement granting it never
# appears in the recipe book at all, which is the state every Heartstead recipe shipped in.
# ---------------------------------------------------------------------------------------------

SIGIL = "heartstead:sigil"
VAULT_SIGIL = "heartstead:vault_sigil"
CORE_SIGIL = "heartstead:core_sigil"
SOUL = "heartstead:primed_soul_core"
VERDANT = "heartstead:primed_verdant_core"
PASTORAL = "heartstead:primed_pastoral_core"
LITHIC = "heartstead:primed_lithic_core"

UNLOCKS = {
    # holding a Sigil: the three surcharges, and the Codex, which costs no Sigil at all
    "core_sigil": SIGIL,
    "heart_sigil": SIGIL,
    "vault_sigil": SIGIL,
    "codex": SIGIL,

    # holding a Vault Sigil: everything the Vault is made of
    "vault_anchor": VAULT_SIGIL,
    "satchel": VAULT_SIGIL,
    "linked_funnel": VAULT_SIGIL,
    "overworld_vault_sigil": VAULT_SIGIL,
    "nether_vault_sigil": VAULT_SIGIL,
    "end_vault_sigil": VAULT_SIGIL,
    "vault_pouch": "heartstead:satchel",

    # holding a Core Sigil: the four families and their reverts
    "primed_soul_core": CORE_SIGIL,
    "primed_verdant_core": CORE_SIGIL,
    "primed_pastoral_core": CORE_SIGIL,
    "primed_lithic_core": CORE_SIGIL,
    "revert_primed_soul_core": SOUL,
    "revert_primed_verdant_core": VERDANT,
    "revert_primed_pastoral_core": PASTORAL,
    "revert_primed_lithic_core": LITHIC,

    # holding a primed core: that family's housing and that family's specialists
    "soul_cage": SOUL,
    "verdant_planter": VERDANT,
    "paddock": PASTORAL,
    "quarry_node": LITHIC,
    "guardian_core": SOUL,
    "wither_skull_core": SOUL,
    "ender_core": SOUL,
    "shulker_core": SOUL,
    "ominous_core": SOUL,
    "barter_core": SOUL,
    "golem_core": SOUL,
    "slime_core": SOUL,
    "tidal_core": SOUL,
    "geode_core": LITHIC,
    "apiary_core": PASTORAL,

    # holding a Codex
    "tome": "heartstead:codex",
}

# heartstead:core_upgrade is a custom recipe type applied in the housing screen, not a book entry.
NOT_IN_BOOK = {"core_upgrade"}


def recipe_ingredients(recipe):
    """Every distinct item or tag a recipe consumes, in a stable order.

    The staging gate above is a *progression* statement and is often not an ingredient at all —
    the Anchor is amethyst, copper, a pearl and a barrel, gated on a Vault Sigil. That gap is why
    the second unlock path below exists, and this is where it reads the truth from.
    """
    with open(os.path.join(RECIPE_DIR, f"{recipe}.json")) as handle:
        data = json.load(handle)

    kind = data["type"]
    if kind == "minecraft:crafting_shaped":
        raw = list(data["key"].values())
    elif kind == "minecraft:crafting_shapeless":
        raw = list(data["ingredients"])
    else:
        raise SystemExit(
            f"{recipe}: unhandled recipe type {kind}. Teach recipe_ingredients about it, or add "
            "it to NOT_IN_BOOK if it is not a recipe-book entry."
        )

    return list(dict.fromkeys(raw))


# ---------------------------------------------------------------------------------------------
# emit
# ---------------------------------------------------------------------------------------------

def write(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def build_tree():
    lang = {}
    for name, parent, icon, kind, title, description, criteria in TREE:
        advancement = {
            "display": {
                "icon": {"id": icon},
                "title": {"translate": f"advancements.heartstead.{name}.title"},
                "description": {"translate": f"advancements.heartstead.{name}.description"},
                "frame": kind,
                "show_toast": parent is not None,
                "announce_to_chat": False,
                "hidden": False,
            },
            "criteria": criteria,
        }
        if parent is None:
            advancement["display"]["background"] = BACKGROUND
        else:
            advancement["parent"] = f"heartstead:{parent}"

        # three_ways is the one AND in the tree: §1.1's tension is only visible when you are
        # holding all three at once, so each criterion gets its own requirement group.
        if name == "three_ways":
            advancement["requirements"] = [[key] for key in criteria]
        else:
            advancement["requirements"] = [list(criteria)]

        write(os.path.join(ADV, f"{name}.json"), advancement)
        lang[f"advancements.heartstead.{name}.title"] = title
        lang[f"advancements.heartstead.{name}.description"] = description
    return lang


def build_recipe_unlocks():
    recipes = {
        os.path.splitext(f)[0] for f in os.listdir(RECIPE_DIR) if f.endswith(".json")
    } - NOT_IN_BOOK

    missing = recipes - set(UNLOCKS)
    if missing:
        raise SystemExit(
            "These recipes have no unlock and so would never appear in the recipe book: "
            + ", ".join(sorted(missing))
        )
    stale = set(UNLOCKS) - recipes
    if stale:
        raise SystemExit("UNLOCKS names recipes that do not exist: " + ", ".join(sorted(stale)))

    write(os.path.join(ADV, "recipes/root.json"), {
        "criteria": {"impossible": {"trigger": "minecraft:impossible"}},
        "requirements": [["impossible"]],
    })

    for recipe in sorted(recipes):
        write(os.path.join(ADV, f"recipes/{recipe}.json"), {
            "parent": "heartstead:recipes/root",
            "criteria": {
                "has_the_recipe": {
                    "trigger": "minecraft:recipe_unlocked",
                    "conditions": {"recipe": f"heartstead:{recipe}"},
                },
                "has_ingredient": has(UNLOCKS[recipe]),
                "has_components": has_all(recipe_ingredients(recipe)),
            },
            # OR: the staging gate, or you can already make the thing, or something else granted
            # the recipe. The second is not redundant — the gate is usually not an ingredient, so
            # without it the book hides recipes the player could craft this second, which for the
            # T1 Vault (§2.1: the first Anchor is free) hid the whole system behind a Nether trip
            # for the ender eye in a Vault Sigil.
            "requirements": [["has_the_recipe", "has_ingredient", "has_components"]],
            "rewards": {"recipes": [f"heartstead:{recipe}"]},
        })
    return len(recipes)


def merge_lang(entries):
    """Rewrite the advancements.* block in place, leaving every other key where it was."""
    with open(LANG) as handle:
        existing = json.load(handle)
    kept = {k: v for k, v in existing.items() if not k.startswith("advancements.heartstead.")}
    kept.update(entries)
    with open(LANG, "w") as handle:
        json.dump(kept, handle, indent=2, ensure_ascii=False)
        handle.write("\n")


def main():
    # A renamed advancement must not leave its old file behind: the game would load both and the
    # tree would grow a branch nothing generates any more.
    if os.path.isdir(ADV):
        shutil.rmtree(ADV)

    lang = build_tree()
    unlocked = build_recipe_unlocks()
    merge_lang(lang)
    print(f"{len(TREE)} advancements, {unlocked} recipe unlocks, {len(lang)} lang keys")


if __name__ == "__main__":
    main()
