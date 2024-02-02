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
$newVarFnCall13 = print_r($array, true);
$newVarFnCall18 = nonExistingFunction();
$newVarFnCall23 = existingFunction();
$newVarFnCall29 = existingFunctionWithParameter(123);
$someVar = <<<HEREDOC_DELIMITER
This could be $very 
 complicated{$newVarFnCall13}<br /> Non-existing function:{$newVarFnCall18}<br /> Existing function:{$newVarFnCall23}<br /> Existing function with parameter (123):{$newVarFnCall29}
HEREDOC_DELIMITER;
