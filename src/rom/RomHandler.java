package rom;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import constants.RomConstants;
import data.Card;
import data.Cards;
import util.ByteUtils;

public class RomHandler
{
	private RomHandler() {}
	
	static boolean verifyRom(byte[] rawBytes)
	{
		int index = RomConstants.HEADER_LOCATION;
		for (byte headerByte : RomConstants.HEADER)
		{
			if (headerByte != rawBytes[index++])
			{
				return false;
			}
		}
		
		return true;
	}
	
	public static RomData readRom(File romFile) throws IOException
	{
		RomData rom = new RomData();
		
		rom.rawBytes = readRaw(romFile);
		verifyRom(rom.rawBytes);
		
		Texts allText = readAllTextFromPointers(rom.rawBytes);
		rom.allCards = readAllCardsFromPointers(rom.rawBytes, allText);
		rom.idsToText = allText;
		
		return rom;
	}
	
	public static void writeRom(RomData rom, File romFile) throws IOException
	{
		setAllCardsAnPointers(rom.rawBytes, rom.allCards, rom.idsToText);
		setTextAndPointers(rom.rawBytes, rom.idsToText);
		
		writeRaw(rom.rawBytes, romFile);
	}
	
	private static byte[] readRaw(File romFile) throws IOException 
	{
		return Files.readAllBytes(romFile.toPath());
	}
	
	private static void writeRaw(byte[] rawBytes, File romFile)
	{
		try (FileOutputStream fos = new FileOutputStream(romFile))
		{
			fos.write(rawBytes);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static Cards<Card> readAllCardsFromPointers(byte[] rawBytes, Texts allText)
	{
		Cards<Card> allCards = new Cards<>();
		Set<Short> convertedTextPtrs = new HashSet<>();

		// Read the text based on the pointer map in the rom
		int ptrIndex = RomConstants.CARD_POINTERS_LOC;
		int cardIndex = 0;

		// Read each pointer one at a time until we reach the ending null pointer
		while ((cardIndex = (short) ByteUtils.readLittleEndian(
					rawBytes, ptrIndex, RomConstants.CARD_POINTER_SIZE_IN_BYTES)
				) != 0)
		{
			cardIndex += RomConstants.CARD_POINTER_OFFSET;
			Card.addCardFromBytes(rawBytes, cardIndex, allText, convertedTextPtrs, allCards);

			// Move our text pointer to the next pointer
			ptrIndex += RomConstants.CARD_POINTER_SIZE_IN_BYTES;
		}
		
		allText.removeTextAtIds(convertedTextPtrs);
		return allCards;
	}
	
	private static Texts readAllTextFromPointers(byte[] rawBytes)
	{
		Texts textMap = new Texts();
		 
		 // Read the text based on the pointer map in the rom
		int ptrIndex = RomConstants.TEXT_POINTERS_LOC;
		int ptr = 0;
		int textIndex = 0;
		int firstPtr = Integer.MAX_VALUE;
		
		// Read each pointer one at a time until we reach the ending null pointer
		while (ptrIndex < firstPtr)
		{
			ptr = (int) ByteUtils.readLittleEndian(
					rawBytes, ptrIndex, RomConstants.TEXT_POINTER_SIZE_IN_BYTES) + 
					RomConstants.TEXT_POINTER_OFFSET;
			if (ptr < firstPtr)
			{
				firstPtr = ptr;
			}
			
			// Find the ending null byte
			textIndex = ptr;
			while (rawBytes[++textIndex] != 0x00);
			
			// Read the string to the null char (but not including it)
			textMap.insertTextAtNextId(new String(rawBytes, ptr, textIndex - ptr));

			// Move our text pointer to the next pointer
			ptrIndex += RomConstants.TEXT_POINTER_SIZE_IN_BYTES;
		}
		
		return textMap;
	}
	
	private static void setAllCardsAnPointers(byte[] bytes, Cards<Card> cards, Texts allText)
	{
		// First write the 0 index "null" text pointer
		int ptrIndex = RomConstants.CARD_POINTERS_LOC - RomConstants.CARD_POINTER_SIZE_IN_BYTES;
		for (int byteIndex = 0; byteIndex < RomConstants.CARD_POINTER_SIZE_IN_BYTES; byteIndex++)
		{
			bytes[ptrIndex++] = 0;
		}
		
		// determine where the first text will go based off the number of text we have
		// The first null pointer was already taken care of so we don't need to handle it 
		// here but we still need to handle the last null pointer
		int cardIndex = RomConstants.CARD_POINTERS_LOC + (cards.count() + 1) * RomConstants.CARD_POINTER_SIZE_IN_BYTES;
		
		List<Card> sorted = cards.toSortedList();
		for (Card card : sorted)
		{
			// Write the pointer
			ByteUtils.writeLittleEndian(cardIndex - RomConstants.CARD_POINTER_OFFSET, bytes, ptrIndex, RomConstants.CARD_POINTER_SIZE_IN_BYTES);
			ptrIndex += RomConstants.CARD_POINTER_SIZE_IN_BYTES;
			
			// Write the card
			cardIndex = card.convertToIdsAndWriteData(bytes, cardIndex, allText);
		}

		// Write the null pointer at the end of the cards pointers
		for (int byteIndex = 0; byteIndex < RomConstants.CARD_POINTER_SIZE_IN_BYTES; byteIndex++)
		{
			bytes[ptrIndex++] = 0;
		}
		
		// Until it is determined to be necessary, don't worry about padding remaining space with 0xff
	}
	
	private static void setTextAndPointers(byte[] rawBytes, Texts ptrToText) throws IOException
	{
		// First write the 0 index "null" text pointer
		int ptrIndex = RomConstants.TEXT_POINTERS_LOC - RomConstants.TEXT_POINTER_SIZE_IN_BYTES;
		for (int byteIndex = 0; byteIndex < RomConstants.TEXT_POINTER_SIZE_IN_BYTES; byteIndex++)
		{
			rawBytes[ptrIndex++] = 0;
		}
		
		// determine where the first text will go based off the number of text we have
		// The null pointer was already taken care of so we don't need to handle it here
		int textIndex = RomConstants.TEXT_POINTERS_LOC + ptrToText.count() * RomConstants.TEXT_POINTER_SIZE_IN_BYTES;

		// Not sure why but every 0x4000 bytes there's a boundary that
		// we can't write past without getting garbly-gook text. It aligns
		// presumably causally with the text location offset
		int nextTextStorageBlock = RomConstants.TEXT_POINTER_OFFSET + RomConstants.TEXT_STORAGE_CHUNK_SIZE;
		
		// Now for each text, write the pointer then write the text at that address
		// Note we intentionally do a index based lookup instead of iteration in order to
		// ensure that the IDs are sequential as they need to be (i.e. there are no gaps)
		// We start at 1 because 0 is a null ptr
		for (short textId = 1; textId < ptrToText.count() + 1; textId++)
		{			
			// First get the text and determine if we need to shift the index to 
			// avoid a storage block boundary
			byte[] textBytes = ptrToText.getAtId(textId).getBytes();
			if (textIndex + textBytes.length + 2 > nextTextStorageBlock)
			{
				textIndex = nextTextStorageBlock;
				nextTextStorageBlock += RomConstants.TEXT_STORAGE_CHUNK_SIZE;
			}

			// Write the pointer
			ByteUtils.writeLittleEndian(textIndex - RomConstants.TEXT_POINTER_OFFSET, rawBytes, ptrIndex, RomConstants.TEXT_POINTER_SIZE_IN_BYTES);
			ptrIndex += RomConstants.TEXT_POINTER_SIZE_IN_BYTES;
			
			// Now write the text
			System.arraycopy(textBytes, 0, rawBytes, textIndex, textBytes.length);
			textIndex += textBytes.length;
			
			// Write trailing null
			rawBytes[textIndex++] = 0x00;
		}
		
		// Until it is determined to be necessary, don't worry about padding remaining space with 0xff
	}
}
