import ballerina/test;
import ballerina/io;

(any|error)[] outputs = [];
int count = 0;

@test:Mock {
    moduleName: "ballerina/io",
    functionName: "println"
}
public function mockPrint(any|error... s) {
    outputs[count] = string.convert(s[0]);
    count += 1;
}

@test:Config
function testFunc() {
    main();
    test:assertEquals(outputs[0], "Create CUSTOMER table status: 0");
    test:assertEquals(outputs[1], "Create SALARY table status: 0");
    test:assertEquals(outputs[2], "Inserted row count: 1");
    test:assertEquals(outputs[3], "Generated key: 1");
    test:assertEquals(outputs[4], "Insert to SALARY table status: 1");
    test:assertEquals(outputs[5], "Transaction committed");
    test:assertEquals(outputs[6], "Drop Table CUSTOMER status: 0");
    test:assertEquals(outputs[7], "Drop Table SALARY status: 0");
}
