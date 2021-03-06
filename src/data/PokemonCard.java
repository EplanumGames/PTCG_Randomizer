package data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import constants.CardDataConstants.*;
import rom.Texts;
import util.ByteUtils;

public class PokemonCard extends Card 
{
	public static final int TOTAL_SIZE_IN_BYTES = 65;
	public static final int SIZE_OF_PAYLOAD_IN_BYTES = TOTAL_SIZE_IN_BYTES - CARD_COMMON_SIZE;
	public static final int MAX_NUM_MOVES = 2;
	
	byte hp; // TODO: non multiples of 10?
	EvolutionStage stage;
	OneLineText prevEvoName;
	
	private Move[] moves;
	
	byte retreatCost; // TODO: max allowed?
	WeaknessResistanceType weakness; // TODO: Allows multiple?
	WeaknessResistanceType resistance; // TODO: Allows multiple?
	public OneLineText pokemonCategory; // TODO: Investigate
	public byte pokedexNumber;
	byte unknownByte1; // TODO: Always 0?
	byte level; // TODO: Investigate No gameplay impact?
	short length; //TODO: One byte is feet, another is inches - separate them // TODO: Investigate No gameplay impact?
	short weight; // TODO: Investigate No gameplay impact?
	PokeDescription description;
	 // TODO: At least somewhat tracks with evo stage in asm files - 19 for first stage, 16 for second stage, 0 for final stage?
	byte unknownByte2;

	public PokemonCard()
	{
		super();
		
		prevEvoName = new OneLineText();
		moves = new Move[MAX_NUM_MOVES];
		for (int moveIndex = 0; moveIndex < MAX_NUM_MOVES; moveIndex++)
		{
			moves[moveIndex] = new Move();
		}
		pokemonCategory = new OneLineText();
		description = new PokeDescription();
	}
	
	public PokemonCard(PokemonCard toCopy)
	{
		super(toCopy);
		
		hp = toCopy.hp;
		stage = toCopy.stage;
		prevEvoName = new OneLineText(toCopy.prevEvoName);
		moves = new Move[MAX_NUM_MOVES];
		for (int moveIndex = 0; moveIndex < MAX_NUM_MOVES; moveIndex++)
		{
			moves[moveIndex] = new Move(toCopy.moves[moveIndex]);
		}
		retreatCost = toCopy.retreatCost;
		weakness = toCopy.weakness;
		resistance = toCopy.resistance;
		pokemonCategory = new OneLineText(toCopy.pokemonCategory);
		pokedexNumber = toCopy.pokedexNumber;
		unknownByte1 = toCopy.unknownByte1;
		level = toCopy.level;
		length = toCopy.length;
		weight = toCopy.weight;
		description = new PokeDescription(toCopy.description);
		unknownByte2 = toCopy.unknownByte2;
	}
	
	public List<Move> getAllMoves()
	{
		ArrayList<Move> movesList = new ArrayList<>();
		for(Move move : moves)
		{
			movesList.add(new Move(move));
		}
		return movesList;
	}
	
	public int getNumMoves()
	{
		int numMoves = 0;
		for (Move move : moves)
		{
			if (!move.isEmpty())
			{
				numMoves++;
			}
		}
		return numMoves;
	}
	
	public void setMove(Move move, int moveSlot)
	{
		try
		{
			moves[moveSlot] = new Move(move);
		}
		catch (IndexOutOfBoundsException ioobe)
		{
			throw new IllegalArgumentException("Bad move slot " + moveSlot + "was passed!");
		}
	}
	
	public void setMoves(List<Move> newMoves)
	{
		if (newMoves.size() != moves.length)
		{
			throw new IllegalArgumentException("Bad number of moves (" + newMoves.size() + ") was passed!");
		}
		
		for (int moveIndex = 0; moveIndex < moves.length; moveIndex++)
		{
			moves[moveIndex] = new Move(newMoves.get(moveIndex));
		}
	}
	
	public void sortMoves()
	{
		Move tempMove;
		boolean needsSwap;
		for (int moveIndex = 0; moveIndex < moves.length - 1; moveIndex++)
		{
			needsSwap = false;
			// Move empty moves to the end
			if (moves[moveIndex].isEmpty() || moves[moveIndex + 1].isEmpty())
			{
				if (moves[moveIndex].isEmpty() && !moves[moveIndex + 1].isEmpty() )
				{
					needsSwap = true;
				}
			}
			// Move poke powers first
			else if (!moves[moveIndex].isPokePower() && moves[moveIndex + 1].isPokePower())
			{
				needsSwap = true;
			}
			else
			{
				int numColorless1 = moves[moveIndex].getCost(EnergyType.COLORLESS);
				int numColorless2 = moves[moveIndex + 1].getCost(EnergyType.COLORLESS);
				int numNonColorless1 = moves[moveIndex].getNonColorlessEnergyCosts();
				int numNonColorless2 = moves[moveIndex + 1].getNonColorlessEnergyCosts();
				
				// Move higher total energies last
				if (numColorless1 + numNonColorless1 > numColorless2 + numNonColorless2)
				{
					needsSwap = true;
				}
				else if (numColorless1 + numNonColorless1 == numColorless2 + numNonColorless2)
				{
					// If equal num, move more non-colorless last
					if (numNonColorless1 > numNonColorless2)
					{
						needsSwap = true;
					}
					else if (numNonColorless1 == numNonColorless2)
					{
						// If equal move higher damage last
						if (moves[moveIndex].damage > moves[moveIndex + 1].damage)
						{
							needsSwap = true;
						}
						// If equal, moves with effects last
						else if (moves[moveIndex].damage == moves[moveIndex + 1].damage &&
								!moves[moveIndex].description.isEmpty() && moves[moveIndex + 1].description.isEmpty())
						{
							needsSwap = true;
						}
					}
				}
			}
				
			if (needsSwap)
			{
				tempMove = moves[moveIndex];
				moves[moveIndex] = moves[moveIndex + 1];
				moves[moveIndex + 1] = tempMove;
				moveIndex = 0; // restart sort loop
			}
		}
	}
	
	@Override
	public String toString()
	{
		StringBuilder builder = new StringBuilder();
		builder.append(super.toString() + 
				"\nPokedex Number = " + pokedexNumber + 
				"\nDesciption = " + description.toString() + 
				"\nHP = " + hp +
				"\nStage = " + stage + 
				"\nPrevEvolution = " + prevEvoName.toString() +
				"\nRetreatCost = " + retreatCost +
				"\nWeakness = " + weakness +
				"\nResistance = " + resistance  +
				"\nMoves");

		for (int moveIndex = 0; moveIndex < MAX_NUM_MOVES; moveIndex++)
		{
			builder.append("\n" + moves[moveIndex].toString());
		}
		return builder.toString();
	}
	
	@Override
	public int readDataAndConvertIds(byte[] cardBytes, int startIndex, Texts idToText, Set<Short> textIdsUsed) 
	{
		readCommonNameAndDataAndConvertIds(cardBytes, startIndex, idToText, textIdsUsed);
		
		int index = startIndex + Card.CARD_COMMON_SIZE;
		hp = cardBytes[index++];
		stage = EvolutionStage.readFromByte(cardBytes[index++]);
		
		// Read the prev evolution
		index = prevEvoName.readDataAndConvertIds(cardBytes, index, idToText, textIdsUsed);

		for (int moveIndex = 0; moveIndex < MAX_NUM_MOVES; moveIndex++)
		{
			index = moves[moveIndex].readDataAndConvertIds(cardBytes, index, name, idToText, textIdsUsed);
		}
		
		retreatCost = cardBytes[index++];
		weakness = WeaknessResistanceType.readFromByte(cardBytes[index++]);
		resistance = WeaknessResistanceType.readFromByte(cardBytes[index++]);

		index = pokemonCategory.readDataAndConvertIds(cardBytes, index, idToText, textIdsUsed);
		
		pokedexNumber = cardBytes[index++];
		unknownByte1 = cardBytes[index++];
		level = cardBytes[index++];
		length = ByteUtils.readAsShort(cardBytes, index);
		index += 2;
		weight = ByteUtils.readAsShort(cardBytes, index);
		index += 2;
		
		index = description.readDataAndConvertIds(cardBytes, index, idToText, textIdsUsed);
		
		unknownByte2 = cardBytes[index++];
		
		return index;
	}

	@Override
	public int convertToIdsAndWriteData(byte[] cardBytes, int startIndex, Texts idToText) 
	{
		int index = convertCommonToIdsAndWriteData(cardBytes, startIndex, idToText);
		
		cardBytes[index++] = hp;
		cardBytes[index++] = stage.getValue();
		
		index = prevEvoName.convertToIdsAndWriteData(cardBytes, index, idToText);

		sortMoves();
		for (int moveIndex = 0; moveIndex < MAX_NUM_MOVES; moveIndex++)
		{
			index = moves[moveIndex].convertToIdsAndWriteData(cardBytes, index, name, idToText);
		}
		
		cardBytes[index++] = retreatCost;
		cardBytes[index++] = weakness.getValue();
		cardBytes[index++] = resistance.getValue();

		index = pokemonCategory.convertToIdsAndWriteData(cardBytes, index, idToText);
		
		cardBytes[index++] = pokedexNumber;
		cardBytes[index++] = unknownByte1;
		cardBytes[index++] = level;
		ByteUtils.writeAsShort(length, cardBytes, index);
		index += 2;
		ByteUtils.writeAsShort(weight, cardBytes, index);
		index += 2;

		index = description.convertToIdsAndWriteData(cardBytes, index, idToText);
		
		cardBytes[index++] = unknownByte2;
		return index;
	}
}
