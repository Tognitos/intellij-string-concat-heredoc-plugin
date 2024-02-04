<?php
$givenCls = new stdClass();
$givenCls->value = 'value of stdClass';
$newVarPhpExpression1 = 5;
$newVarPhpExpression2 = true;
$newVarPhpExpression3 = null;
$arr = [
    'entry1',
    <<<HEREDOC_DELIMITER
{$newVarPhpExpression1}a{$newVarPhpExpression2}{$newVarPhpExpression3}{$givenCls->value}
HEREDOC_DELIMITER,
    'entry3'
];
