package com.periut.retrotweaks.feature.recipe;

import com.periut.retrotweaks.RetroTweaks;
import com.periut.retrotweaks.config.Config;
import com.periut.retrotweaks.mixin.recipe.CraftingRecipeManagerAccessor;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.recipe.CraftingRecipe;
import net.minecraft.recipe.CraftingRecipeManager;
import net.minecraft.recipe.ShapedRecipe;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.SmeltingRecipeManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * All crafting and smelting changes, merged from MostlyModernRecipes, NowObtainableRecipes,
 * GameplayEssentials, AnnoyanceFix and UniTweaks.
 *
 * <p>Installed from {@code CraftingRecipeManager}'s constructor, right after vanilla has filled and
 * sorted its list. That is deliberately not an API hook: RetroTweaks must work with no API installed,
 * and the constructor is the one moment that is correct on every setup.
 *
 * <p>Vanilla's list is kept, so {@link #apply()} can be called again whenever the config changes -
 * it restores vanilla and re-applies from scratch, which is why none of the recipe options need a
 * restart.
 *
 * <p>Where the source mods appended a competing recipe and relied on the list's sort order to make
 * theirs win, this edits the vanilla recipe in place instead. Changing "3 slabs" to "6 slabs" is a
 * one-field change to the existing recipe rather than twelve new 3x3 recipes that have to out-sort
 * it, so the result does not depend on sort order at all - and it cannot be shadowed by a mod that
 * sorts the list again afterwards.
 *
 * <p>Mod-added recipes are preserved across {@link #apply()} calls. The initial {@code apply()} runs
 * before mods register anything, so recipes added by other mods after that point are not part of
 * the {@link #pristineRecipes} snapshot and would be silently dropped by the next {@code apply()}
 * (which fires from {@code ConfigManager.load()}). We identify them by identity: anything not in
 * the vanilla snapshot and not created by our own tweaks is a mod recipe and is re-added at the end
 * of every {@code apply()}.
 */
public final class RecipeTweaks {

	private RecipeTweaks() {}

	/**
	 * Vanilla's recipe list exactly as it was built, kept so the tweaks can be undone and redone
	 * without a restart. Recipe objects are shared with the live list, so output counts changed in
	 * place are restored from {@link #PRISTINE_OUTPUTS} rather than from here.
	 */
	private static List<CraftingRecipe> pristineRecipes;

	/** Identity set of the pristine recipes, for O(1) "is this vanilla?" checks during apply(). */
	private static final Set<CraftingRecipe> pristineSet =
		Collections.newSetFromMap(new IdentityHashMap<>());

	/**
	 * Identity set of every recipe this class has ever added to the live list: replacements created
	 * by {@link #replace()}, and all recipes appended by our own tweaks. Mod recipes are the ones
	 * that are neither in {@link #pristineSet} nor in this set - see {@link #apply()}.
	 */
	private static final Set<CraftingRecipe> tweakCreated =
		Collections.newSetFromMap(new IdentityHashMap<>());

	/** Output stack counts as vanilla built them, by recipe identity. */
	private static final Map<CraftingRecipe, Integer> PRISTINE_OUTPUTS = new IdentityHashMap<>();

	/**
	 * {@link SmeltingRecipeManager}'s furnace recipes exactly as its constructor built them, kept so
	 * the obtainable-ore smelting recipes (coal/redstone/lapis ore -> ingot/dye) can be undone the
	 * same way the crafting side is: {@link #smelting()} clears the live map back to this snapshot
	 * and re-adds only what the config currently asks for, instead of only ever calling addRecipe.
	 */
	private static Map<Integer, ItemStack> pristineSmelting;

	/**
	 * Keys of every smelting entry this class added itself, so {@link #restoreSmelting()} can drop
	 * our own entries while preserving anything a mod registered. Without this, turning a config
	 * option off would leave its smelting recipe in the live map.
	 */
	private static final Set<Integer> tweakSmeltingKeys = new HashSet<>();

	private static CraftingRecipeManager manager;

	/** Called once, from the recipe manager's constructor. */
	public static void install(CraftingRecipeManager recipeManager) {
		manager = recipeManager;
		// The recipe manager is built after Block and Item, so this is the earliest safe point.
		StackSizes.markReady();
		pristineRecipes = new ArrayList<>(recipeManager.getRecipes());
		for (CraftingRecipe recipe : pristineRecipes) {
			pristineSet.add(recipe);
			ItemStack output = recipe.getOutput();
			if (output != null) PRISTINE_OUTPUTS.put(recipe, output.count);
		}
		// RetroTweaks is the only thing that ever calls addRecipe on the furnace's singleton, so the
		// first touch of it here - before apply() below can add anything - is the true vanilla state.
		pristineSmelting = new HashMap<>(SmeltingRecipeManager.getInstance().getRecipes());
		apply();
	}

	/**
	 * Rebuilds the recipe list from the config. Safe to call at any time - the config screen calls
	 * it on save - because it always starts from vanilla rather than layering onto what is there.
	 *
	 * <p>Recipes registered by other mods (identified as anything not in the vanilla snapshot and
	 * not created by our own tweaks) are captured before the rebuild and re-added after, so the
	 * second {@code apply()} - the one fired from {@code ConfigManager.load()} - no longer wipes
	 * modded recipes registered between {@link #install} and then.
	 */
	public static void apply() {
		if (manager == null) return; // recipes have not been built yet; install() will call us

		List<CraftingRecipe> recipes = manager.getRecipes();

		// Capture mod-added recipes before we wipe the list. Anything that is not a vanilla recipe
		// and not one of ours is by definition a recipe another mod registered.
		List<CraftingRecipe> modRecipes = new ArrayList<>();
		for (CraftingRecipe r : recipes) {
			if (!pristineSet.contains(r) && !tweakCreated.contains(r)) {
				modRecipes.add(r);
			}
		}

		recipes.clear();
		recipes.addAll(pristineRecipes);
		for (CraftingRecipe recipe : pristineRecipes) {
			ItemStack output = recipe.getOutput();
			Integer count = PRISTINE_OUTPUTS.get(recipe);
			if (output != null && count != null) output.count = count;
		}
		restoreSmelting();

		// Track every recipe added from here to the end of the tweak section below as our own, so
		// the next apply() call can tell our recipes apart from mod-added ones.
		int tweaksStartIndex = recipes.size();

		// Companion fix for Config.EQUIPMENT.bowsHaveDurability, not gated behind enableRecipes below:
		// once bows can wear, a worn bow must still craft a dispenser, whether or not the discretionary
		// recipe tweaks are switched on.
		equipmentRecipes(recipes);
		// Also not gated behind enableRecipes: the four non-vanilla fish smelt into their own cooked
		// species exactly as unconditionally as vanilla's RAW_FISH -> COOKED_FISH does, whenever
		// RetroAPI has actually supplied them (see Fishing#nonVanillaFishAvailable).
		// FISHINFOODTWEAKS DISABLED - re-enable by uncommenting. fishRecipes() is already a no-op while the fish
		// are unregistered, so this is belt and braces rather than load-bearing.
		// fishRecipes();

		if (!Config.RECIPES.enableRecipes) {
			RetroTweaks.LOGGER.info("Recipe tweaks are switched off; vanilla recipes restored.");
			// Track the (usually zero) recipes equipmentRecipes may have appended, then restore mod
			// recipes regardless of whether our own tweaks ran.
			trackAdded(recipes, tweaksStartIndex);
			recipes.addAll(modRecipes);
			return;
		}

		CraftingRecipeManagerAccessor access = (CraftingRecipeManagerAccessor) manager;
		modernRecipes(recipes, access);
		tweakedRecipes(recipes, access);
		obtainableRecipes(recipes, access);
		smelting();

		// Record everything our tweaks appended above as ours, then re-add the mod recipes we
		// captured at the top. Adding them here (and only here) means a following apply() sees them
		// as mod recipes again, correctly, since they are neither pristine nor ours.
		trackAdded(recipes, tweaksStartIndex);
		recipes.addAll(modRecipes);
	}

	/** Marks every recipe from {@code fromIndex} to the end of the list as created by us. */
	private static void trackAdded(List<CraftingRecipe> recipes, int fromIndex) {
		for (int i = fromIndex; i < recipes.size(); i++) {
			tweakCreated.add(recipes.get(i));
		}
	}

	// ---------------------------------------------------------------- equipment

	/**
	 * From MiscTweaks' {@code RecipeListener}. {@code ShapedRecipe.matches()} requires an exact
	 * damage match unless the ingredient's damage is -1, and vanilla's dispenser recipe pins the
	 * bow ingredient to damage 0. Once {@link Config.Equipment#bowsHaveDurability} lets bows accrue
	 * wear, that exact match can never be satisfied again, so a used bow could no longer be used to
	 * craft a dispenser. Rebuilding the recipe with a damage=-1 (wildcard) bow ingredient fixes it.
	 */
	private static void equipmentRecipes(List<CraftingRecipe> recipes) {
		if (Config.EQUIPMENT.bowsHaveDurability) {
			replace(recipes, Block.DISPENSER.id, shaped(3, 3, new ItemStack(Block.DISPENSER, 1),
				new ItemStack(Block.COBBLESTONE, 1), new ItemStack(Block.COBBLESTONE, 1), new ItemStack(Block.COBBLESTONE, 1),
				new ItemStack(Block.COBBLESTONE, 1), new ItemStack(Item.BOW, 1, -1), new ItemStack(Block.COBBLESTONE, 1),
				new ItemStack(Block.COBBLESTONE, 1), new ItemStack(Item.REDSTONE, 1), new ItemStack(Block.COBBLESTONE, 1)));
		}
	}

	/**
	 * Furnace recipes for the four non-vanilla fish species (see {@code Fishing}), raw -> cooked,
	 * from FishinFoodTweaks' own {@code RecipeListener}. A no-op until RetroAPI has registered the
	 * items themselves; re-added on every {@link #apply()} because {@link #restoreSmelting()} just
	 * cleared the live table back to what {@link #install} snapshotted before these ever existed.
	 */
	private static void fishRecipes() {
		if (!com.periut.retrotweaks.feature.fishing.Fishing.nonVanillaFishAvailable()) return;
		SmeltingRecipeManager smelting = SmeltingRecipeManager.getInstance();
		Item[] raw = com.periut.retrotweaks.feature.fishing.Fishing.RAW;
		Item[] cooked = com.periut.retrotweaks.feature.fishing.Fishing.COOKED;
		for (int i = 0; i < raw.length; i++) {
			smelting.addRecipe(raw[i].id, new ItemStack(cooked[i]));
			tweakSmeltingKeys.add(raw[i].id);
		}
	}

	// ---------------------------------------------------------------- modern

	private static void modernRecipes(List<CraftingRecipe> recipes, CraftingRecipeManagerAccessor access) {
		Config.ModernRecipes modern = Config.RECIPES.modern;

		if (modern.shapelessFlintAndSteel) {
			access.retrotweaks$addShapeless(new ItemStack(Item.FLINT_AND_STEEL, 1), Item.FLINT, Item.IRON_INGOT);
		}
		if (modern.shapelessMushroomStew) {
			access.retrotweaks$addShapeless(new ItemStack(Item.MUSHROOM_STEW, 1), Item.BOWL, Block.BROWN_MUSHROOM, Block.RED_MUSHROOM);
		}
		if (modern.shapelessChestMinecart) {
			access.retrotweaks$addShapeless(new ItemStack(Item.CHEST_MINECART, 1), Item.MINECART, Block.CHEST);
		}
		if (modern.shapelessFurnaceMinecart) {
			access.retrotweaks$addShapeless(new ItemStack(Item.FURNACE_MINECART, 1), Item.MINECART, Block.FURNACE);
		}
		if (modern.shapelessStickyPiston) {
			access.retrotweaks$addShapeless(new ItemStack(Block.STICKY_PISTON, 1), Block.PISTON, Item.SLIMEBALL);
		}

		if (modern.booksRequireLeather) {
			// Vanilla is three paper in a column. Modern is shapeless with a leather cover.
			replace(recipes, Item.BOOK.id, shapeless(new ItemStack(Item.BOOK, 1),
				new ItemStack(Item.PAPER), new ItemStack(Item.PAPER), new ItemStack(Item.PAPER), new ItemStack(Item.LEATHER)));
		}

		if (modern.woolRedyeing) {
			// Any wool plus a dye becomes that dye's colour. Dye metadata counts down from 15 as
			// wool metadata counts up from 0, which is why the two indices are mirrored.
			for (int colour = 0; colour < 16; colour++) {
				access.retrotweaks$addShapeless(
					new ItemStack(Block.WOOL, 1, colour),
					new ItemStack(Block.WOOL, 1, -1),
					new ItemStack(Item.DYE, 1, 15 - colour));
			}
		}

		if (modern.sixSlabsPerCraft) {
			setOutputCount(recipes, Block.SLAB.id, 6);
		}
		if (modern.threeLaddersPerCraft) {
			setOutputCount(recipes, Block.LADDER.id, 3);
		}
		if (modern.threeSignsPerCraft) {
			setOutputCount(recipes, Item.SIGN.id, 3);
		}
		if (modern.threeDoorsPerCraft) {
			setOutputCount(recipes, Item.WOODEN_DOOR.id, 3);
			setOutputCount(recipes, Item.IRON_DOOR.id, 3);
		}

		if (modern.oneStonePerButton) {
			replace(recipes, Block.BUTTON.id, shapeless(new ItemStack(Block.BUTTON, 1), new ItemStack(Block.STONE, 1, -1)));
		}

		if (modern.modernFenceRecipe) {
			// Vanilla: six sticks for two fences. Modern: four planks and two sticks for three.
			replace(recipes, Block.FENCE.id, shaped(3, 2, new ItemStack(Block.FENCE, 3),
				new ItemStack(Block.PLANKS, 1, -1), new ItemStack(Item.STICK), new ItemStack(Block.PLANKS, 1, -1),
				new ItemStack(Block.PLANKS, 1, -1), new ItemStack(Item.STICK), new ItemStack(Block.PLANKS, 1, -1)));
		}

		if (modern.snowLayerRecipe) {
			access.retrotweaks$addShaped(new ItemStack(Block.SNOW, 6), "###", '#', Block.SNOW_BLOCK);
		}

		if (modern.modernGoldenApple) {
			// Vanilla surrounds the apple with eight gold BLOCKS, modern uses eight ingots.
			replace(recipes, Item.GOLDEN_APPLE.id, shaped(3, 3, new ItemStack(Item.GOLDEN_APPLE, 1),
				gold(), gold(), gold(),
				gold(), new ItemStack(Item.APPLE), gold(),
				gold(), gold(), gold()));
		}
	}

	private static ItemStack gold() {
		return new ItemStack(Item.GOLD_INGOT);
	}

	// ---------------------------------------------------------------- tweaked

	private static void tweakedRecipes(List<CraftingRecipe> recipes, CraftingRecipeManagerAccessor access) {
		Config.TweakedRecipes tweaked = Config.RECIPES.tweaked;

		if (tweaked.shapelessJackOLantern) {
			access.retrotweaks$addShapeless(new ItemStack(Block.JACK_O_LANTERN, 1), Block.PUMPKIN, Block.TORCH);
		}
		if (tweaked.stairsPerCraft != 4) {
			setOutputCount(recipes, Block.WOODEN_STAIRS.id, tweaked.stairsPerCraft);
			setOutputCount(recipes, Block.COBBLESTONE_STAIRS.id, tweaked.stairsPerCraft);
		}
		if (tweaked.trapdoorsPerCraft != 2) {
			setOutputCount(recipes, Block.TRAPDOOR.id, tweaked.trapdoorsPerCraft);
		}
	}

	// ---------------------------------------------------------------- obtainable

	private static void obtainableRecipes(List<CraftingRecipe> recipes, CraftingRecipeManagerAccessor access) {
		Config.ObtainableRecipes obtainable = Config.RECIPES.obtainable;

		if (obtainable.cobweb) {
			access.retrotweaks$addShaped(new ItemStack(Block.COBWEB, 1), "X X", " X ", "X X", 'X', Item.STRING);
		}
		if (obtainable.coalOre) {
			access.retrotweaks$addShaped(new ItemStack(Block.COAL_ORE, 1), "XXX", "XYX", "XXX", 'X', Block.STONE, 'Y', Item.COAL);
		}
		if (obtainable.ironOre) {
			access.retrotweaks$addShaped(new ItemStack(Block.IRON_ORE, 1), "XXX", "XYX", "XXX", 'X', Block.STONE, 'Y', Item.IRON_INGOT);
		}
		if (obtainable.goldOre) {
			access.retrotweaks$addShaped(new ItemStack(Block.GOLD_ORE, 1), "XXX", "XYX", "XXX", 'X', Block.STONE, 'Y', Item.GOLD_INGOT);
		}
		if (obtainable.diamondOre) {
			access.retrotweaks$addShaped(new ItemStack(Block.DIAMOND_ORE, 1), "XXX", "XYX", "XXX", 'X', Block.STONE, 'Y', Item.DIAMOND);
		}
		if (obtainable.redstoneOre) {
			access.retrotweaks$addShaped(new ItemStack(Block.REDSTONE_ORE, 1), "XYX", "YYY", "XYX", 'X', Block.STONE, 'Y', Item.REDSTONE);
		}
		if (obtainable.lapisOre) {
			access.retrotweaks$addShaped(new ItemStack(Block.LAPIS_ORE, 1), "XYX", "YYY", "XYX", 'X', Block.STONE, 'Y', new ItemStack(Item.DYE, 1, 4));
		}
		if (obtainable.sponge) {
			access.retrotweaks$addShaped(new ItemStack(Block.SPONGE, 1), "XYX", "YZY", "XYX",
				'X', Item.SLIMEBALL, 'Y', new ItemStack(Block.WOOL, 1, 4), 'Z', Item.BUCKET);
		}
		if (obtainable.doubleSlab) {
			access.retrotweaks$addShaped(new ItemStack(Block.DOUBLE_SLAB, 1), "X", "X", 'X', new ItemStack(Block.SLAB, 1, -1));
			access.retrotweaks$addShapeless(new ItemStack(Block.SLAB, 2), new ItemStack(Block.DOUBLE_SLAB, 1, -1));
		}
		if (obtainable.apple) {
			access.retrotweaks$addShapeless(new ItemStack(Item.APPLE, 1),
				Item.SUGAR, Item.SEEDS, new ItemStack(Item.DYE, 1, 1), Block.SAPLING);
		}
		if (obtainable.deadBush) {
			access.retrotweaks$addShapeless(new ItemStack(Block.DEAD_BUSH, 1), Block.SAPLING, Block.FIRE);
		}
		if (obtainable.fire) {
			access.retrotweaks$addShapeless(new ItemStack(Block.FIRE, 3), Item.FLINT_AND_STEEL);
		}
		if (obtainable.grass) {
			access.retrotweaks$addShapeless(new ItemStack(Block.GRASS_BLOCK, 1), Block.DIRT, Item.SEEDS);
		}
		if (obtainable.ice) {
			// One recipe per batch size so a full grid is not wasted on a single block of ice.
			for (int batch = 1; batch <= 3; batch++) {
				Object[] ingredients = new Object[batch * 3];
				for (int i = 0; i < batch; i++) {
					ingredients[i * 3] = Item.WATER_BUCKET;
					ingredients[i * 3 + 1] = Block.SNOW_BLOCK;
					ingredients[i * 3 + 2] = Block.SNOW_BLOCK;
				}
				access.retrotweaks$addShapeless(new ItemStack(Block.ICE, batch), ingredients);
			}
		}
	}

	/**
	 * Clears the furnace's live recipe map back to the {@link #pristineSmelting} snapshot, preserving
	 * any entry a mod registered. Called unconditionally at the top of {@link #apply()}, the same way
	 * the crafting list is reset from {@link #pristineRecipes} - so turning an ore option off, or
	 * switching the whole recipes toggle off, removes its smelting recipe instead of leaving it
	 * registered for the rest of the session. Mod smelting recipes (e.g. a mod's {@code addSmelting}
	 * calls) survive because they are neither in the pristine snapshot nor in
	 * {@link #tweakSmeltingKeys}.
	 */
	private static void restoreSmelting() {
		Map<Integer, ItemStack> recipes = SmeltingRecipeManager.getInstance().getRecipes();

		// Preserve anything not in the pristine snapshot and not our own tweak-added entry.
		Map<Integer, ItemStack> modAdded = new HashMap<>();
		for (Map.Entry<Integer, ItemStack> entry : recipes.entrySet()) {
			int key = entry.getKey();
			if (!pristineSmelting.containsKey(key) && !tweakSmeltingKeys.contains(key)) {
				modAdded.put(key, entry.getValue());
			}
		}

		recipes.clear();
		recipes.putAll(pristineSmelting);
		recipes.putAll(modAdded);
	}

	private static void smelting() {
		Config.ObtainableRecipes obtainable = Config.RECIPES.obtainable;
		SmeltingRecipeManager smelting = SmeltingRecipeManager.getInstance();
		// The three ores vanilla never taught the furnace about, added only when their craftable
		// counterpart is on - otherwise the recipe would exist for a block nothing can produce.
		if (obtainable.coalOre) {
			smelting.addRecipe(Block.COAL_ORE.id, new ItemStack(Item.COAL, 1));
			tweakSmeltingKeys.add(Block.COAL_ORE.id);
		}
		if (obtainable.redstoneOre) {
			smelting.addRecipe(Block.REDSTONE_ORE.id, new ItemStack(Item.REDSTONE, 1));
			tweakSmeltingKeys.add(Block.REDSTONE_ORE.id);
		}
		if (obtainable.lapisOre) {
			smelting.addRecipe(Block.LAPIS_ORE.id, new ItemStack(Item.DYE, 1, 4));
			tweakSmeltingKeys.add(Block.LAPIS_ORE.id);
		}
	}

	// ---------------------------------------------------------------- list helpers

	/**
	 * Changes how many a recipe produces, for every recipe producing that id.
	 *
	 * <p>Mutating the output stack works because vanilla's {@code craft} reads the count off it
	 * every time (shaped) or copies it (shapeless), and it leaves the recipe's position in the list
	 * alone, so nothing else about crafting shifts.
	 */
	private static void setOutputCount(List<CraftingRecipe> recipes, int itemId, int count) {
		for (CraftingRecipe recipe : recipes) {
			ItemStack output = recipe.getOutput();
			if (output != null && output.itemId == itemId) output.count = count;
		}
	}

	/**
	 * Swaps the first recipe producing {@code itemId} for a different one, keeping its position.
	 * The replacement is registered in {@link #tweakCreated} so the next {@link #apply()} call can
	 * tell it apart from a mod-added recipe (which is preserved, not replaced).
	 */
	private static void replace(List<CraftingRecipe> recipes, int itemId, CraftingRecipe replacement) {
		tweakCreated.add(replacement);
		for (int i = 0; i < recipes.size(); i++) {
			ItemStack output = recipes.get(i).getOutput();
			if (output != null && output.itemId == itemId) {
				recipes.set(i, replacement);
				return;
			}
		}
		// Another mod already replaced or removed it; adding ours keeps the feature working.
		recipes.add(replacement);
	}

	private static ShapedRecipe shaped(int width, int height, ItemStack output, ItemStack... input) {
		return new ShapedRecipe(width, height, input, output);
	}

	private static ShapelessRecipe shapeless(ItemStack output, ItemStack... input) {
		List<ItemStack> list = new ArrayList<>(List.of(input));
		return new ShapelessRecipe(output, list);
	}
}