package org.maymichael.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.maymichael.services.DataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Slf4j
public class DemoController {

    private final DataService dataService;


    @PostMapping("/demo1")
    public void saveWithRepository(){
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
    public void saveWithKeyValueAdapter(){
        // demo2: save with key value adapter

        // advantage: more control
        // disadvantage: still deletes entries and reinserts, if just using insert
        // BUT supports PartialUpdate (demo4), which overcomes this problem)
    }

    @PostMapping("/demo3")
    public void saveWithPipeline(){
        // demo3: save with pipeline key value adapter

        // advantage: much faster because of pipeline (does not wait for results, no delete)
        // disadvantage: lots of safety checks will be removed, custom implementation, not part of spring data core
        // should only be used for a batch insert, not to update existing keys!
    }

    @PostMapping("/demo4")
    public void time(){
        // demo4: run demo1, demo2 and demo3 multiple times and get average time spend
        // overview of demo 1, demo 2 and demo 3
    }

    @PostMapping("/demo5")
    public void partialUpdate(){
        // demo5: partial update
    }

    @PostMapping("/demo6")
    public void partialRead(){
        // demo6: partial read
    }

    @PostMapping("/demo7")
    public void demo7(){
        dataService.testMassGet();
    }

    @PostMapping("/demo8")
    @ResponseBody
    public ResponseEntity<HttpStatus> demo8(@RequestParam(value = "numItems", defaultValue = "10") int items, @RequestParam(value = "strategy", defaultValue = "2") int strategy){
        // save big chunk of data
        if(items < 0 ) return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        if(strategy >= DataService.SaveStrategy.values().length || strategy < 0) {
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        dataService.saveBigData(items, DataService.SaveStrategy.values()[strategy]);
        return new ResponseEntity<>(HttpStatus.OK);
    }

}
