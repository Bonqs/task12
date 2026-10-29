package lv.bootcamp.shelter.repository;

import lv.bootcamp.shelter.model.AdoptionRecord;
import lv.bootcamp.shelter.model.Animal;
import lv.bootcamp.shelter.model.AnimalStatus;
import lv.bootcamp.shelter.model.AnimalType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AdoptionRecordRepositoryTest {

    @Autowired
    private AdoptionRecordRepository adoptionRecordRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findRecentAdoptionsByAnimalType_shouldFilterByTypeAndDate() {
        LocalDateTime now = LocalDateTime.now();

        Animal rex = entityManager.persist(newAnimal("Rex", AnimalType.DOG));
        Animal cake = entityManager.persist(newAnimal("Cake", AnimalType.CAT));
        Animal max = entityManager.persist(newAnimal("Max", AnimalType.DOG));
        entityManager.persist(newRecord(rex, "bob@example.com", now.minusDays(2)));
        entityManager.persist(newRecord(cake, "john@example.com", now.minusDays(5)));
        entityManager.persist(newRecord(max, "max@example.com", now.minusDays(10)));
        entityManager.flush();


        List<AdoptionRecord> result = adoptionRecordRepository
                .findRecentAdoptionsByAnimalType(AnimalType.DOG, now.minusDays(7));
        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getAnimal().getName()).isEqualTo("Rex");
    }

    private Animal newAnimal(String name, AnimalType type) {
        Animal animal = new Animal();
        animal.setName(name);
        animal.setType(type);
        animal.setStatus(AnimalStatus.ADOPTED);
        return animal;
    }

    private AdoptionRecord newRecord(Animal animal, String email, LocalDateTime adoptedAt) {
        AdoptionRecord record = new AdoptionRecord();
        record.setAnimal(animal);
        record.setAdopterEmail(email);
        record.setAdoptedAt(adoptedAt);
        return record;
    }
}