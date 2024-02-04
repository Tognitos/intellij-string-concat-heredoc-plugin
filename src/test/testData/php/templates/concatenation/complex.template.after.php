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
$newVarFnCall1 = print_r($array, true);
$newVarFnCall2 = nonExistingFunction();
$newVarFnCall3 = existingFunction();
$newVarFnCall4 = existingFunctionWithParameter(123);
$someVar = <<<HEREDOC_DELIMITER
This could be $very \n complicated{$newVarFnCall1}<br /> Non-existing function:{$newVarFnCall2}<br /> Existing function:{$newVarFnCall3}<br /> Existing function with parameter (123):{$newVarFnCall4}
HEREDOC_DELIMITER;
