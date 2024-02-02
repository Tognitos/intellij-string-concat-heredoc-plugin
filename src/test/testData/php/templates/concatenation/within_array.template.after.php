<?php
$givenCls = new stdClass();
$givenCls->value = 'value of stdClass';
$newVarPhpExpression3 = 5;
$newVarPhpExpression5 = true;
$newVarPhpExpression7 = null;
$newVarFnCall19 = functionCall();
$arr = [
    'entry1',
    <<<HEREDOC_DELIMITER
{$newVarPhpExpression3}a{$newVarPhpExpression5}{$newVarPhpExpression7}\n{$givenCls->value}\n End :){$newVarFnCall19}
HEREDOC_DELIMITER,
    'entry3'
];
