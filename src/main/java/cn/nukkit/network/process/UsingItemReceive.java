package cn.nukkit.network.process;

import cn.nukkit.inventory.transaction.data.UseItemData;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.PlayerActionPacket;
import cn.nukkit.network.protocol.PlayerAuthInputPacket;
import cn.nukkit.network.protocol.types.AuthInputAction;

/**
 * Shared receive-side rules for {@code Player.setUsingItem}.
 * <p>
 * Official Bedrock / Java-via-ViaBedrock start hold-to-use from AuthInput
 * {@link AuthInputAction#START_USING_ITEM} (and, on older movement, PlayerAction
 * {@link PlayerActionPacket#ACTION_START_ITEM_USE_ON} / {@link PlayerActionPacket#ACTION_START_USING_ITEM}).
 * MOT previously parsed those bits and then ignored them, so eating/drawing never
 * set {@code DATA_FLAG_ACTION} and other players never saw the animation.
 * <p>
 * This helper is package-visible from {@code Player} and unit-tested without a live
 * session so the many cancel paths (equalsFast, second CLICK_AIR, sprint, default
 * PlayerAction fallthrough, same-slot MobEquipment) stay explicit.
 */
public final class UsingItemReceive {

    /**
     * MOT 860 / NetEase Java proxy debug. Enabled with {@code -Dnukkit.debugUsingItem=true},
     * env {@code NUKKIT_DEBUG_USING_ITEM=1}, or {@code debug-settings.debug-using-item} in
     * nukkit-mot.yml. Keep off in production: CLICK_AIR and AuthInput fire every use start.
     */
    public static volatile boolean DEBUG = propertyOrEnvEnabled();

    private static boolean propertyOrEnvEnabled() {
        return Boolean.getBoolean("nukkit.debugUsingItem")
                || "1".equals(System.getenv("NUKKIT_DEBUG_USING_ITEM"))
                || "true".equalsIgnoreCase(System.getenv("NUKKIT_DEBUG_USING_ITEM"));
    }

    public static void applyServerConfig(boolean enabled) {
        DEBUG = propertyOrEnvEnabled() || enabled;
    }

    private UsingItemReceive() {
    }

    public static boolean isHoldToUseItem(Item item) {
        return item != null && item.getId() != 0 && item.canRelease();
    }

    public static boolean isClickAirUse(int transactionType, Object transactionData) {
        if (transactionType != InventoryTransactionPacket.TYPE_USE_ITEM) {
            return false;
        }
        if (!(transactionData instanceof UseItemData useItemData)) {
            return false;
        }
        return useItemData.actionType == InventoryTransactionPacket.USE_ITEM_ACTION_CLICK_AIR;
    }

    /**
     * MOT {@code equalsFast} is id+meta (and custom namespace). A Via encode that still
     * decodes to a different runtime/legacy pair would otherwise drop the start and never
     * {@code setUsingItem}. Rewrite the packet stack to the server held item so the native
     * CLICK_AIR path can run.
     */
    public static boolean shouldRewriteClickAirHeldItem(Item packetItem, Item serverItem) {
        if (!isHoldToUseItem(serverItem)) {
            return false;
        }
        return packetItem == null || !packetItem.equalsFast(serverItem);
    }

    /**
     * A second CLICK_AIR while already using is treated as {@code onUse(ticksUsed)} and
     * always {@code setUsingItem(false)}. Keep that as the duration-ready finish, but
     * ignore an immediate duplicate start so Java USE_ITEM + USE_ITEM_ON cannot abort
     * auto-complete after 0 ticks.
     */
    public static boolean shouldIgnoreDuplicateClickAirStart(boolean alreadyUsing, boolean holdToUse,
                                                             int ticksUsed) {
        return alreadyUsing && holdToUse && ticksUsed < 2;
    }

    public static boolean shouldStartUsingFromClickAir(boolean spawnedAlive, boolean spectatorBlocked,
                                                       boolean alreadyUsing, boolean holdToUse,
                                                       boolean onClickAir) {
        return spawnedAlive && !spectatorBlocked && !alreadyUsing && holdToUse && onClickAir;
    }

    public static boolean authInputStartsUsingItem(PlayerAuthInputPacket packet) {
        return packet != null
                && packet.getInputData() != null
                && packet.getInputData().contains(AuthInputAction.START_USING_ITEM);
    }

    /**
     * AuthInput START_USING_ITEM is the official start bit. MOT used to parse and ignore
     * it. Start from the <em>server</em> held stack, not the optional attached item TX,
     * because NetEase 860 Java clients do not attach AuthInput item interaction for food/bow.
     */
    public static boolean shouldStartUsingFromAuthInput(boolean spawnedAlive, boolean spectatorBlocked,
                                                        boolean alreadyUsing, boolean holdToUse,
                                                        boolean startUsingItemFlag) {
        return spawnedAlive && !spectatorBlocked && !alreadyUsing && holdToUse && startUsingItemFlag;
    }

    /**
     * START_SPRINTING used to always {@code setUsingItem(false)}. Java eat/draw cancels
     * sprint on the same tick Via emits StartUsingItem; if MOT still sees both bits,
     * keep using and skip the sprint cancel.
     */
    public static boolean shouldKeepUsingDespiteStartSprinting(boolean alreadyUsing, boolean holdToUse,
                                                               boolean startUsingItemFlag) {
        return alreadyUsing && holdToUse || startUsingItemFlag && holdToUse;
    }

    public static boolean isStartUsingPlayerAction(int action) {
        return action == PlayerActionPacket.ACTION_START_ITEM_USE_ON
                || action == PlayerActionPacket.ACTION_START_USING_ITEM;
    }

    public static boolean isStopUsingPlayerAction(int action) {
        return action == PlayerActionPacket.ACTION_STOP_ITEM_USE_ON;
    }

    /**
     * Unknown PlayerAction values used to fall through to {@code setUsingItem(false)}.
     * Keep that for real interrupts, but START/STOP item-use-on and START_USING_ITEM
     * must not.
     */
    public static boolean shouldClearUsingOnUnhandledPlayerAction(int action) {
        return !isStartUsingPlayerAction(action) && !isStopUsingPlayerAction(action);
    }

    /**
     * MobEquipment always cleared using, including the same-slot confirmation Java sends
     * before CLICK_AIR. Only a real hotbar switch is an interrupt.
     */
    public static boolean shouldClearUsingOnMobEquipment(boolean alreadyUsing, int currentHeldIndex,
                                                         int packetHotbarSlot) {
        return alreadyUsing && currentHeldIndex != packetHotbarSlot;
    }

    public static String describeItem(Item item) {
        if (item == null) {
            return "null";
        }
        return item.getNamespaceId() + "#" + item.getId() + ":" + item.getDamage()
                + " canRelease=" + item.canRelease()
                + " duration=" + item.getUseDuration();
    }
}
