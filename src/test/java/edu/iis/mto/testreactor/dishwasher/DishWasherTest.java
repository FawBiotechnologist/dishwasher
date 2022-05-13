package edu.iis.mto.testreactor.dishwasher;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import edu.iis.mto.testreactor.dishwasher.engine.Engine;
import edu.iis.mto.testreactor.dishwasher.engine.EngineException;
import edu.iis.mto.testreactor.dishwasher.pump.PumpException;
import edu.iis.mto.testreactor.dishwasher.pump.WaterPump;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DishWasherTest {
	@Mock
	private Engine engineMock;
	@Mock
	private WaterPump waterPumpMock;
	@Mock
	private DirtFilter dirtFilterMock;
	@Mock
	private Door doorMock;
	private DishWasher dishWasherToTest;
	private final WashingProgram rinseWashingProgram = WashingProgram.RINSE;
	private final WashingProgram anyNonRinseWashingProgram = WashingProgram.ECO;
	private final int timeForRinseProgram = rinseWashingProgram.getTimeInMinutes();
	private final int timeForNonRinseProgram = WashingProgram.ECO.getTimeInMinutes();
	private final FillLevel irrelevantFillLevel = FillLevel.FULL;
	private final ProgramConfiguration configWithTabletsRinseProgram = ProgramConfiguration.builder()
			.withProgram(rinseWashingProgram)
			.withFillLevel(irrelevantFillLevel)
			.withTabletsUsed(true)
			.build();
	private final ProgramConfiguration configWithoutTabletsNonRinseProgram = ProgramConfiguration.builder()
			.withProgram(anyNonRinseWashingProgram)
			.withFillLevel(irrelevantFillLevel)
			.withTabletsUsed(false)
			.build();

	@BeforeEach
	void setUp() {
		dishWasherToTest = new DishWasher(waterPumpMock, engineMock, dirtFilterMock, doorMock);
	}

	@Test
	void itCompiles() {
		assertThat(true, Matchers.equalTo(true));
	}

	@Test
	void properRunWithTabletsExpectingSuccessWithNoErrors() {
		when(doorMock.closed()).thenReturn(true);
		when(dirtFilterMock.capacity()).thenReturn(DishWasher.MAXIMAL_FILTER_CAPACITY + 0.01);
		RunResult result = dishWasherToTest.start(configWithTabletsRinseProgram);

		Assertions.assertEquals(Status.SUCCESS, result.getStatus());
		Assertions.assertEquals(timeForRinseProgram, result.getRunMinutes());
	}

	@Test
	void properRunWithoutTabletsExpectingSuccessWithNoErrors() {
		when(doorMock.closed()).thenReturn(true);
		RunResult result = dishWasherToTest.start(configWithoutTabletsNonRinseProgram);

		Assertions.assertEquals(Status.SUCCESS, result.getStatus());
		Assertions.assertEquals(timeForNonRinseProgram, result.getRunMinutes());
	}

	@Test
	void doorsAreOpenExpectingStatusDoorOpen() {
		when(doorMock.closed()).thenReturn(false);
		RunResult result = dishWasherToTest.start(configWithTabletsRinseProgram);
		Assertions.assertEquals(Status.DOOR_OPEN, result.getStatus());
		Assertions.assertEquals(0, result.getRunMinutes());
	}

	@Test
	void filterIsDirtyExpectingStatusErrorFilter() {
		when(doorMock.closed()).thenReturn(true);
		when(dirtFilterMock.capacity()).thenReturn(DishWasher.MAXIMAL_FILTER_CAPACITY);
		RunResult result = dishWasherToTest.start(configWithTabletsRinseProgram);

		Assertions.assertEquals(Status.ERROR_FILTER, result.getStatus());
		Assertions.assertEquals(0, result.getRunMinutes());
	}

	@Test
	void engineIsDamagedExpectingStatusErrorProgram() throws EngineException {
		when(doorMock.closed()).thenReturn(true);
		when(dirtFilterMock.capacity()).thenReturn(DishWasher.MAXIMAL_FILTER_CAPACITY + 1);
		doThrow(EngineException.class).when(engineMock).runProgram(any());
		RunResult result = dishWasherToTest.start(configWithTabletsRinseProgram);

		Assertions.assertEquals(Status.ERROR_PROGRAM, result.getStatus());
		Assertions.assertEquals(0, result.getRunMinutes());
	}

	@Test
	void pumpIsDamagedExpectingStatusErrorPump() throws PumpException {
		when(doorMock.closed()).thenReturn(true);
		when(dirtFilterMock.capacity()).thenReturn(DishWasher.MAXIMAL_FILTER_CAPACITY + 1);
		doThrow(PumpException.class).when(waterPumpMock).drain();
		RunResult result = dishWasherToTest.start(configWithTabletsRinseProgram);

		Assertions.assertEquals(Status.ERROR_PUMP, result.getStatus());
		Assertions.assertEquals(0, result.getRunMinutes());
	}

	@Test
	void properOrderOfOperationWhileSuccessfulWashing() throws PumpException, EngineException {
		when(doorMock.closed()).thenReturn(true);
		when(dirtFilterMock.capacity()).thenReturn(DishWasher.MAXIMAL_FILTER_CAPACITY + 1);
		InOrder order = Mockito.inOrder(dirtFilterMock, doorMock, engineMock, waterPumpMock);

		RunResult result = dishWasherToTest.start(configWithTabletsRinseProgram);
		order.verify(doorMock).closed();
		order.verify(dirtFilterMock).capacity();
		order.verify(doorMock).lock();
		order.verify(waterPumpMock).pour(irrelevantFillLevel);
		order.verify(engineMock).runProgram(any());
		order.verify(waterPumpMock).drain();
		order.verify(doorMock).unlock();
		order.verifyNoMoreInteractions();

		Assertions.assertEquals(Status.SUCCESS, result.getStatus());
		Assertions.assertEquals(timeForRinseProgram, result.getRunMinutes());
	}


}
