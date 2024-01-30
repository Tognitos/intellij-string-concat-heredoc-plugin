<?php
const A = 'a';
$newVarPhpExpression1 = A;
$someVar = <<<HEREDOC_DELIMITER
{$newVarPhpExpression1}bc
HEREDOC_DELIMITER;
