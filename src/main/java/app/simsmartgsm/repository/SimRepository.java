package app.simsmartgsm.repository;
// SimRepository.java

import app.simsmartgsm.entity.Sim;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SimRepository extends MongoRepository<Sim, String> {
    Optional<Sim> findByPhoneNumber(String phoneNumber);

    Optional<Sim> findFirstByPhoneNumber(String phoneNumber);

    Optional<Sim> findByComName(String phoneNumber);

    List<Sim> findAllByDeviceNameAndComName(String deviceName, String comName);

    List<Sim> findByDeviceName(String deviceName);

}
