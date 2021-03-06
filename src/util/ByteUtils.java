package util;

public class ByteUtils 
{
	// TODO consider storing everything as a long but only writing so many bits
	// Or maybe just store as bytes?
	
	public static final int MAX_BYTE_VALUE = 0xff;
	public static final int MIN_BYTE_VALUE = 0x00;

	public static final int MAX_HEX_CHAR_VALUE = 0xf;
	public static final int MIN_HEX_CHAR_VALUE = 0x0;
	
	public static final int BYTE_UPPER_HEX_CHAR_MASK = 0xf0;
	public static final int BYTE_LOWER_HEX_CHAR_MASK = 0x0f;

	public static void printBytes(byte[] bytes, int index, int bytesPerNumber, int numberToPrint)
	{
		String formatString = "0x%" + bytesPerNumber*2 + "X";
		for (int i = 0; i < numberToPrint; i++)
		{
			System.out.println(String.format(formatString, 
					readLittleEndian(bytes, index + i * bytesPerNumber, bytesPerNumber)));
		}
	}

	public static int unsignedCompareBytes(byte b1, byte b2)
	{
		return unsignedCompare(b1, b2, 1);
	}
	
	public static int unsignedCompare(int i1, int i2, int numBytes)
	{
		// Since shorts are signed, we need to do some bit magic
		// to get them to their unsigned values so we sort correctly
		long l1 = i1;
		if (l1 < 0)
		{
			l1 += 1 << (numBytes * 8);
		}
		
		long l2 = i2;
		if (l2 < 0)
		{
			l2 += 1 << (numBytes * 8);
		}

		if (l1 < l2)
		{
			return -1;
		}
		else if (l1 > l2)
		{
			return 1;
		}
		return 0;
	}

	public static byte readUpperHexChar(byte value)
	{
		return (byte) ((value & BYTE_UPPER_HEX_CHAR_MASK) >> 4);
	}

	public static byte readLowerHexChar(byte value)
	{
		return (byte) (value & BYTE_LOWER_HEX_CHAR_MASK);
	}

	public static byte packHexCharsToByte(byte upper, byte lower)
	{
		if (upper > MAX_HEX_CHAR_VALUE || upper < MIN_HEX_CHAR_VALUE)
		{
			throw new IllegalArgumentException("Upper bit (" + upper + 
					" must be a hex char value unshifted (i.e. between " +
					MIN_HEX_CHAR_VALUE + " and " +  MAX_HEX_CHAR_VALUE + ")");
		}
		if (lower > MAX_HEX_CHAR_VALUE || lower < MIN_HEX_CHAR_VALUE)
		{
			throw new IllegalArgumentException("Lower bit (" + lower + 
					" must be a hex char value");
		}
		return (byte) (upper << 4 & 0xff | lower);
	}

	public static short readAsShort(byte[] byteArray, int index) 
	{	
		//little endian
		return  (short) readLittleEndian(byteArray, index, 2);
	}

	public static void writeAsShort(short value, byte[] byteArray, int index) 
	{	
		writeLittleEndian(value, byteArray, index, 2);
	}

	public static long readLittleEndian(byte[] byteArray, int index, int numBytes) 
	{	
		if (numBytes > 8)
		{
			throw new IllegalArgumentException(
					"readLittleEndian: Bytes must fit in a long (i.e. be less than 8)" +
							" Was given " + numBytes);
		}

		long number = 0;
		for (int j = numBytes - 1; j >= 0; j--)
		{
			number = number << 8;
			// Its a pain because bytes are signed so we need to make sure when the
			// byte is promoted here that it only takes the last digits or else if its
			// > byte's max signed value, it will add FFs to promote it and keep the
			// same negative value whereas we only want the byte values
			number |= byteArray[index + j] & 0xff;
		}
		return number;
	}

	public static void writeLittleEndian(long value, byte[] byteArray, int index, int numBytes) 
	{	
		if (numBytes > 8)
		{
			throw new IllegalArgumentException(
					"writeLittleEndian: Bytes must fit in a long (i.e. be less than 8)." +
							" Was given " + numBytes);
		}

		for (int j = 0; j < numBytes; j++)
		{
			byteArray[index + j] = (byte) (value & 0xff);
			value = value >> 8;
		}
	}
}
