package team.thegoldenhoe.cameraobscura.common.network;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.imageio.ImageIO;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.fml.common.FMLCommonHandler;
import team.thegoldenhoe.cameraobscura.CSModelMetadata;
import team.thegoldenhoe.cameraobscura.Utils;
import team.thegoldenhoe.cameraobscura.common.CameraCapabilities;
import team.thegoldenhoe.cameraobscura.common.ICameraNBT;
import team.thegoldenhoe.cameraobscura.common.ICameraStorageNBT;
import team.thegoldenhoe.cameraobscura.common.ItemProps;
import team.thegoldenhoe.cameraobscura.utils.ModelHandler;

public class PhotoDataHandler {

	private static int photoID = 0;

	private static Map<Integer, TreeSet<MessagePhotoDataToServer>> messageBuffer = new HashMap<>();

	private static Queue<MessagePhotoDataToServer> messageQueue = new ConcurrentLinkedQueue<>();

	public static void addMessage(int uuid, MessagePhotoDataToServer message) {
		TreeSet<MessagePhotoDataToServer> messageSet = messageBuffer.get(Integer.valueOf(uuid));
		if (messageSet == null) {
			messageSet = new TreeSet<MessagePhotoDataToServer>(MessagePhotoDataToServer.COMPARATOR);
			messageSet.add(message);
			messageBuffer.put(Integer.valueOf(uuid), messageSet);
		} else {
			messageSet.add(message);
		}
	}

	/**
	 * Add a message to the message queue for processing
	 */
	public static void bufferMessage(MessagePhotoDataToServer message) {
		messageQueue.add(message);
	}

	/**
	 * Iterate through all queued messages and buffer them for processing
	 */
	public static synchronized void processMessageQueue() {
		while (!messageQueue.isEmpty()) {
			MessagePhotoDataToServer msg = messageQueue.poll();
			addMessage(msg.uuid, msg);
		}
	}

	/**
	 * Iterate through all buffered messages to check if we have received complete data yet
	 */
	public static void processMessageBuffer(World world) {
		Set<Integer> uuids = messageBuffer.keySet();
		List<Integer> completedUuids = new LinkedList<Integer>();

		for (Integer uuid : uuids) {
			TreeSet<MessagePhotoDataToServer> messages = messageBuffer.get(uuid);
			int bytesReceived = 0;

			// Iterate through all messages received for this uuid
			for (MessagePhotoDataToServer message : messages) {
				bytesReceived += message.data.length;

				// If we have received all necessary data
				if (bytesReceived == message.length) {
					completedUuids.add(uuid);
				}
			}
		}

		// Clear out completed entries from the map
		for (Integer uuid : completedUuids) {
			TreeSet<MessagePhotoDataToServer> messages = messageBuffer.get(uuid);
			byte[] bytes = null;
			ByteBuffer buffer = null;
			UUID photographerUUID = null;

			// Iterate through all messages received for this uuid
			for (MessagePhotoDataToServer message : messages) {
				if (bytes == null) {
					bytes = new byte[message.length];
					buffer = ByteBuffer.wrap(bytes);
				}
				if (photographerUUID == null) {
					photographerUUID = UUID.fromString(message.playerUUID);
				}
				
				buffer.put(message.data);
			}

			String savePath = saveImage(createImageFromBytes(bytes));
			
			// TODO: Check type of camera here, decrement film / storage space
			// if digital, save to sd card if present
			// if manual, save to photograph, decrement film level
			if (savePath != null) {
				postImageSaved(world, photographerUUID, savePath);
			} else {
				System.err.println("Save path for image was null. This should never happen, but it did. Look at you, you special person!");
			}
			
			messageBuffer.remove(uuid);
		}
	}
	
	private static void postImageSaved(World world, UUID photographerUUID, String savePath) {
		if (photographerUUID == null) {
			throw new NullPointerException("Photographer UUID is null, which means we can't produce an item. Sorry :(");
		}
		
		EntityPlayer player = world.getPlayerEntityByUUID(photographerUUID);
		ItemStack stack = player.getHeldItemMainhand();
		
		if (stack.isEmpty()) {
			throw new NullPointerException("Camera is null, which means we don't know how to produce the item properly. Sorry :(");
		}
		
		if (stack.getItem() != null && stack.getItem() instanceof ItemProps) {
			if (!player.getHeldItemMainhand().isEmpty() && player.getHeldItemMainhand().getItem() instanceof ItemProps) {
				stack = player.getHeldItemMainhand();
			} else {
				stack = player.getHeldItemOffhand();
			}
			ItemProps camera = (ItemProps)stack.getItem();

			CSModelMetadata data = ModelHandler.getModelFromStack(stack);
			CameraTypes type = data.getCameraType();
			switch (type) {
			case VINTAGE:
				break;
			case POLAROID:
				ICameraNBT cameraCap = stack.getCapability(CameraCapabilities.getCameraCapability(), null);
				ItemStack storageStack = cameraCap.getStackInSlot(0);
				ICameraStorageNBT polaroidStorage = cameraCap.getStorageDevice();
				if (polaroidStorage.canSave()) {
					System.out.println("Num currently saved PRE(server): " + polaroidStorage.getSavedImagePaths().size());
					System.out.println("Saving on the server " + polaroidStorage);
					polaroidStorage.saveImage(savePath);
					System.out.println("Num currently saved POST(server): " + polaroidStorage.getSavedImagePaths().size());
					cameraCap.markDirty();
					System.out.println(cameraCap.getStackInSlot(0).getTagCompound());
					System.out.println(FMLCommonHandler.instance().getEffectiveSide());
					//System.out.println(storageStack);
				} else {
					System.err.println("Somehow between when the picture was taken and saved, the storage device became full. Whoops!");
				}
				
//				EntityPlayer player = world.getPlayerEntityByUUID(photographerUUID);
//				if (player != null) {
//					//player.inventoryContainer.detectAndSendChanges();
//					ItemStack n = new ItemStack(ItemRegistry.polaroidStack);
//					NBTTagCompound nbt2 = new NBTTagCompound();
//					nbt2.setString("Path", savePath);
//					n.setTagCompound(nbt2);
//					// This adds the proper pathed up item to inventory
//					player.addItemStackToInventory(storageStack.copy());
//				} else {
//					System.out.println("Player is null");
//				}
				
				break;
			case DIGITAL:
				ICameraNBT cap = stack.getCapability(CameraCapabilities.getCameraCapability(), null);
				ItemStack sdCard = cap.getStackInSlot(0);
				ICameraStorageNBT digitalStorage = cap.getStorageDevice();
				if (digitalStorage.canSave()) {
					digitalStorage.saveImage(savePath);
					cap.markDirty();
				} else {
					System.err.println("Somehow between when the picture was taken and saved, the storage device became full. Whoops!");
				}
				break;
			case NOT_A_CAMERA:
				System.err.println("Not sure how we got here, but a non camera was trying to save an image. Whoops!");
				break;
			}
		}
	}

	/**
	 * Takes a BufferedImage and saves it to the photographs folder on the server
	 */
	private static String saveImage(BufferedImage image) {
		try {
			String dirName = DimensionManager.getCurrentSaveRootDirectory().getAbsolutePath();
			File directory = new File(dirName, "photographs");
			directory.mkdir();
			File imageFile = Utils.getTimestampedPNGFileForDirectory(directory);
			imageFile = imageFile.getCanonicalFile();
			ImageIO.write(image, "png", imageFile);
			return imageFile.getAbsolutePath();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	private static BufferedImage createImageFromBytes(byte[] bytes) {
		ByteArrayInputStream stream = new ByteArrayInputStream(bytes);
		try {
			return ImageIO.read(stream);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}

	public static int getUniqueID() {
		return photoID++;
	}
}
