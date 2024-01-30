<?php
/**
 * this file has a string concatenations with:
 * - variables
 * - custom function calls
 *  - with params
 *  - without params
 * - native function calls with params (print_r)
 * - \n line-breaks
 * - HTML tags (<br />)
 */
$existing = 'whatever';
$very = 'VERY';
$array = ['a' => 5, 'b'=>'10', 'c'=>true, 'd'=>'😊'];
function existingFunction(): string {
    return 'existingFunction';
}
function existingFunctionWithParameter(int $num): string {
    return "existingFunction $num";
}

// the plugin is not responsible for verifying that the expression is correct, but rather that the behaviour
// remains unaltered after the conversion
$someVar = 'This '
    . "could be $very \n"
    . ' '<caret>
    . 'complicated'
    . print_r($array, true)
    . '<br /> Non-existing function:'
    . nonExistingFunction()
    . '<br /> Existing function:'
    . existingFunction()
    . '<br /> Existing function with parameter (123):'
    . existingFunctionWithParameter(123);
