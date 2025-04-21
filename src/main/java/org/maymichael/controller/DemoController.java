package org.maymichael.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.services.DataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final DataService dataService;


    @PostMapping("/demo1")
    public void saveWithRepository() {
        // demo1 : save with spring repositories (jpa)
        log.info("preparing demo 1 data...");

        // note: problems with spring crud repositories (and the like)
        // no partial update -> always READS the data first, then deletes it and saves it again
        // advantage: if the hash changes by an application update, the values will be consistent with the new api
        // easy to use, if familiar with spring JPA
        // disadvantage: redundant reads and inserts, especially if we only want a partial update
        // PROBLEM: because of the deletion, we CANNOT run getData and saveData in parallel!
        // e.g. what can happen is:
        // T1: saveData - RM hash -> hash not removed from redis repository
        // T2: getData - get HASH -> no Data FOUND -> empty
        // T1: saveData - PUT hash -> data inserted back into redis repository
        // because repository.save is not thread safe (or the lettuce connection is not) it may happen, that we
        // try to get data in between, just when its deleted and not inserted back yet
        // => only option is to use locks then
        // alternative: use keyValueAdapter with Partial Update, which does NOT delete the entire HASH

    }

    @PostMapping("/demo2")
    public void saveWithKeyValueAdapter() {
        // demo2: save with key value adapter

        // advantage: more control
        // disadvantage: still deletes entries and reinserts, if just using insert
        // BUT supports PartialUpdate (demo4), which overcomes this problem)
    }

    @PostMapping("/demo3")
    public void saveWithPipeline() {
        // demo3: save with pipeline key value adapter

        // advantage: much faster because of pipeline (does not wait for results, no delete)
        // disadvantage: lots of safety checks will be removed, custom implementation, not part of spring data core
        // should only be used for a batch insert, not to update existing keys!
    }

    @PostMapping("/demo4")
    public void time() {
        // demo4: run demo1, demo2 and demo3 multiple times and get average time spend
        // overview of demo 1, demo 2 and demo 3
    }

    @PostMapping("/demo5")
    public void partialUpdate() {
        // demo5: partial update
    }

    @PostMapping("/demo6")
    public void partialRead() {
        // demo6: partial read
    }

    @PostMapping("/demo7")
    public void demo7() {
        dataService.testMassGet();
    }

    @PostMapping("/demo8")
    @ResponseBody
    public ResponseEntity<HttpStatus> demo8(@RequestParam(value = "numItems", defaultValue = "10") int items, @RequestParam(value = "strategy", defaultValue = "2") int strategy) {
        // save big chunk of data
        try {
            if (items < 0) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            if (strategy >= DataService.SaveStrategy.values().length || strategy < 0) {
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
            dataService.saveBigData(items, DataService.SaveStrategy.values()[strategy], DataService.SerializerType.KRYO);
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("request failed: ", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private static final int DEMO_MEASUREMENT_SAMPLE_SIZE = 50;
    private static final int DEMO_MEASUREMENT_ATTEMPTS = 10;

    @PostMapping("/demo9")
    @ResponseBody
    public ResponseEntity<HttpStatus> demo9() {
        // compare different serializer approaches and measure time
        try {
            // prepare data beforehand
            for (var serializer : DataService.SerializerType.values()) {
                // warmup
                dataService.createDataSet(DEMO_MEASUREMENT_SAMPLE_SIZE);
                // ignore the first measurement, some data may need to be initialized, e.g. kryo
                dataService.saveData(UUID.randomUUID().toString(), 1, DataService.SaveStrategy.ADAPTER, serializer);
                log.info("-----------------------------------");
                log.info("#####################");
                log.info("##### Starting measurements WRITE - serializer={}", serializer.name());
                StopWatch sw = new StopWatch();
                sw.start();
                for (int i = 0; i < DEMO_MEASUREMENT_ATTEMPTS; i++) {
                    var tid = String.format("%s_%d", serializer.name(), i);
                    dataService.saveData(tid, DEMO_MEASUREMENT_SAMPLE_SIZE, DataService.SaveStrategy.ADAPTER, serializer);
                }
                // get average of all attempts
                sw.stop();
                var totalTimeNanos = sw.lastTaskInfo().getTimeNanos();
                var avgPerAttempt = Duration.of(totalTimeNanos, ChronoUnit.NANOS).toMillis() / DEMO_MEASUREMENT_ATTEMPTS;
                var perItemNanos = Duration.ofNanos(totalTimeNanos / DEMO_MEASUREMENT_SAMPLE_SIZE / DEMO_MEASUREMENT_ATTEMPTS);
                log.info("-");
                log.info("-");
                log.info("#### Stop measurement WRITE - serializer={} totalDuration={}ms avg_perAttempt={}ms avg_perItem={}.{}ms",
                        serializer, Duration.of(totalTimeNanos, ChronoUnit.NANOS).toMillis(), avgPerAttempt,
                        perItemNanos.toMillis(), perItemNanos.toNanosPart() / 100);
                log.info("#####################");
                // raw links to memory in lettuce command cache
                // ... read values are not really valid here
                // can't find how to clear the cache though
                // so just restart the application and do demo10 after having run demo9 for each serializer
                // RAW: http://localhost:8080/demo10?strategy=RAW
                // KRYO: http://localhost:8080/demo10?strategy=KRYO
                // BASE64: http://localhost:8080/demo10?strategy=BASE64
            }
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("request failed: ", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    @PostMapping("/demo10")
    @ResponseBody
    public ResponseEntity<HttpStatus> demo10(@RequestParam(value = "strategy", defaultValue = "RAW") String strategy) {
        try {
            // we need to do this for each strategy once after starting the application to get the most accurate results
            // otherwise data will be cached
            var serializer = DataService.SerializerType.valueOf(strategy);
            var tids = new ArrayList<String>();
            for (int i = 0; i < DEMO_MEASUREMENT_ATTEMPTS; i++) {
                tids.add(String.format("%s_%d", serializer.name(), i));
            }
            var sw = new StopWatch();
            log.info("##### Starting measurements READ - serializer={}", serializer.name());
            sw.start("read");
            for (var tid : tids) {
                var data = dataService.getDataForId(tid);
                // might be layz, so try to get the data here?
                assert data.size() == DEMO_MEASUREMENT_SAMPLE_SIZE;
                for (var d : data) {
                    log.debug("tid={} data_size_raw={} data_size_kryo={} data_size_base64={}", tid,
                            d.getBinaryDataRaw() != null ? d.getBinaryDataRaw().getData().length : 0,
                            d.getBinaryData() != null ? d.getBinaryData().getData().length : 0,
                            d.getBinaryDataBase64() != null ? d.getBinaryDataBase64().getData().length : 0);
                }
            }
            sw.stop();
            var totalTimeNanos = sw.lastTaskInfo().getTimeNanos();
            var avgPerAttempt = Duration.of(totalTimeNanos, ChronoUnit.NANOS).toMillis() / DEMO_MEASUREMENT_ATTEMPTS;
            var perItemNanos = Duration.ofNanos(totalTimeNanos / DEMO_MEASUREMENT_SAMPLE_SIZE / DEMO_MEASUREMENT_ATTEMPTS);
            log.info("#### Stop measurement READ - serializer={} totalDuration={}ms avg_perAttempt={}ms avg_perItem={}.{}ms",
                    serializer, Duration.of(totalTimeNanos, ChronoUnit.NANOS).toMillis(), avgPerAttempt,
                    perItemNanos.toMillis(), perItemNanos.toNanosPart() / 100);
            log.info("-----------------------------------");
            return new ResponseEntity<>(HttpStatus.OK);
        } catch (Exception e) {
            log.error("request failed: ", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        //RAW
        //##### Starting measurements READ - serializer=RAW
        //#### Stop measurement READ - serializer=RAW totalDuration=330ms avg_perAttempt=33ms avg_perItem=0.6618ms

        // KRYO
        //##### Starting measurements READ - serializer=KRYO
        //#### Stop measurement READ - serializer=KRYO totalDuration=332ms avg_perAttempt=33ms avg_perItem=0.6641ms

        // BASE64
        //##### Starting measurements READ - serializer=BASE64
        //#### Stop measurement READ - serializer=BASE64 totalDuration=488ms avg_perAttempt=48ms avg_perItem=0.9769ms

        // -> kryo and raw about equal
        // base64 slower, because of decoding and data overhead

    }

}
