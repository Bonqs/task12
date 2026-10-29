package lv.bootcamp.shelter.service;

import lv.bootcamp.shelter.audit.AuditLogger;
import lv.bootcamp.shelter.audit.RejectionReason;
import lv.bootcamp.shelter.client.NotificationClient;
import lv.bootcamp.shelter.model.AdoptionResult;
import lv.bootcamp.shelter.model.Adopter;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import lv.bootcamp.shelter.repository.AdopterRepository;
import lv.bootcamp.shelter.repository.AnimalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdoptionEligibilityServiceTest {

    @Mock
    private AdopterRepository adopterRepository;

    @Mock
    private AnimalRepository animalRepository;

    @Mock
    private NotificationClient notificationClient;

    @Mock
    private AuditLogger auditLogger;

    @InjectMocks
    private AdoptionEligibilityService service;

    @Test
    void evaluate_shouldRejectWhenAdopterNotFound() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ADOPTER_NOT_FOUND);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluate_shouldRejectWhenAnimalNotFound() {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter()));
        when(animalRepository.findById(2L)).thenReturn(Optional.empty());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_FOUND);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluate_shouldRejectWhenAnimalNotAvailable() {
        Animal animal = animal();
        animal.setStatus(AnimalStatus.ADOPTED);
        mockRepositories(adopter(), animal);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.ANIMAL_NOT_AVAILABLE);
        verifyNoInteractions(auditLogger, notificationClient);
    }

    @Test
    void evaluate_shouldRejectWhenAdopterIs17() {
        Adopter adopter = adopter();
        adopter.setAge(17);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.UNDERAGE);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.UNDERAGE);
        verifyNoInteractions(notificationClient);
    }

    @Test
    void evaluate_shouldApproveWhenAdopterIs18() {
        Adopter adopter = adopter();
        adopter.setAge(18);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_shouldRejectWhenThreePetsAndSmallProperty() {
        Adopter adopter = adopter();
        adopter.setCurrentPetCount(3);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.PET_LIMIT_REACHED);
    }

    @Test
    void evaluate_shouldApproveWhenTwoPetsAndSmallProperty() {
        Adopter adopter = adopter();
        adopter.setCurrentPetCount(2);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_shouldRejectWhenFivePetsAndLargeProperty() {
        Adopter adopter = adopter();
        adopter.setLargeProperty(true);
        adopter.setCurrentPetCount(5);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.PET_LIMIT_REACHED);
    }

    @Test
    void evaluate_shouldApproveWhenFourPetsAndLargeProperty() {
        Adopter adopter = adopter();
        adopter.setLargeProperty(true);
        adopter.setCurrentPetCount(4);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_shouldRejectBirdWithoutPermit() {
        Animal bird = animal();
        bird.setType(AnimalType.BIRD);
        mockRepositories(adopter(), bird);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.EXOTIC_PERMIT_REQUIRED);
        verify(auditLogger).logRejection(1L, 2L, RejectionReason.NO_EXOTIC_PERMIT);
    }

    @Test
    void evaluate_shouldRejectRabbitWithoutPermit() {
        Animal rabbit = animal();
        rabbit.setType(AnimalType.RABBIT);
        mockRepositories(adopter(), rabbit);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectionReasons.EXOTIC_PERMIT_REQUIRED);
    }

    @Test
    void evaluate_shouldApproveBirdWithPermit() {
        Adopter adopter = adopter();
        adopter.setExoticPermit(true);
        Animal bird = animal();
        bird.setType(AnimalType.BIRD);
        mockRepositories(adopter, bird);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
    }

    @Test
    void evaluate_shouldLogApprovalAndSendNotification() {
        Adopter adopter = adopter();
        adopter.setPreviousAdoptions(1);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
        assertThat(result.priorityScore()).isEqualTo(10);
        verify(auditLogger).logApproval(1L, 2L, 10);
        verify(notificationClient).sendApprovalNotification("anna@example.com", "Rex");
    }

    @Test
    void score_shouldNotAddBonusesOnBoundaryValues() {
        Adopter adopter = adopter();
        adopter.setPreviousAdoptions(3);
        Animal animal = animal();
        animal.setAge(7);
        mockRepositories(adopter, animal);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.priorityScore()).isEqualTo(10);
    }

    @Test
    void score_shouldAddAllBonusesAndSubtractPets() {
        Adopter adopter = adopter();
        adopter.setPreviousAdoptions(4);
        adopter.setLargeProperty(true);
        adopter.setCurrentPetCount(4);
        Animal animal = animal();
        animal.setAge(8);
        mockRepositories(adopter, animal);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.priorityScore()).isEqualTo(42);
    }

    @Test
    void score_shouldNotBeNegative() {
        Adopter adopter = adopter();
        adopter.setCurrentPetCount(2);
        mockRepositories(adopter, animal());

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.priorityScore()).isEqualTo(0);
    }

    @Test
    void score_shouldBeZeroWhenAnimalAgeUnknown() {
        Animal animal = animal();
        animal.setAge(null);
        mockRepositories(adopter(), animal);

        AdoptionResult result = service.evaluateAdoption(1L, 2L);

        assertThat(result.approved()).isTrue();
        assertThat(result.priorityScore()).isEqualTo(0);
    }

    private Adopter adopter() {
        Adopter adopter = new Adopter();
        adopter.setId(1L);
        adopter.setName("Anna");
        adopter.setEmail("anna@example.com");
        adopter.setAge(30);
        return adopter;
    }

    private Animal animal() {
        Animal animal = new Animal();
        animal.setId(2L);
        animal.setName("Rex");
        animal.setType(AnimalType.DOG);
        animal.setAge(3);
        animal.setStatus(AnimalStatus.AVAILABLE);
        return animal;
    }

    private void mockRepositories(Adopter adopter, Animal animal) {
        when(adopterRepository.findById(1L)).thenReturn(Optional.of(adopter));
        when(animalRepository.findById(2L)).thenReturn(Optional.of(animal));
    }
}